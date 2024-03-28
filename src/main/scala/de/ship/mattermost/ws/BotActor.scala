package de.ship.mattermost.ws

import de.ship.mattermost.ws.Protocol._
import de.ship.mattermost.ws.model.core._
import akka.NotUsed

import akka.actor.typed.{ActorRef, Behavior, PostStop, PreRestart, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.{KillSwitches, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Source}
import com.typesafe.scalalogging.LazyLogging
import de.ship.mattermost.ws.{HookFlow, Sender}
import de.ship.mattermost.ws.Sender.ActorMessage

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}
import akka.stream.KillSwitch
import java.time.LocalDateTime
import scala.jdk.DurationConverters._
import java.time.Duration
import akka.actor.Cancellable

object BotActor extends LazyLogging {
  // I am just lazy and the args for each state are the same. GC go brrr
  private case class ActorState(
      killSwitch: KillSwitch,
      sender: ActorRef[Sender.Protocol],
      botToken: String,
      hooks: Seq[ActorRef[WebsocketMessage]],
      internalSeq: Int,
      lastReply: LocalDateTime,
      serviceSettings: ServiceSettings,
      pingSchedule: Cancellable
  ) {
    def pinged: ActorState = this.copy(lastReply = LocalDateTime.now())

    // TODO: perhaps it's for the best if the WS sender handles the internal seq
    def sent: ActorState = this.copy(internalSeq = internalSeq + 1)
  }

  private case class IncomingMessage(e: WebsocketMessage) extends BotCommand
  private case class ConnectionError(t: Throwable)        extends BotCommand
  private case object Connected                           extends BotCommand
  private case object ConnectionClosed                    extends BotCommand
  private case object Ping                                extends BotCommand

  def apply(
      websocketUrl: String,
      botToken: String,
      initialHooks: Seq[ActorRef[WebsocketMessage]] = Seq.empty,
      serviceSettings: ServiceSettings = ServiceSettings(),
      clientSettings: Option[ClientConnectionSettings] = None
  ): Behavior[BotCommand] = Behaviors
    .supervise[BotCommand] {
      Behaviors.setup { ctx =>
        implicit val classicSystem = ctx.system.toClassic

        // delegate handling for when we are in a later stage
        // initialHooks.foreach(ctx.self ! RegisterHook(_))

        val wsSettings = clientSettings.getOrElse(
          ClientConnectionSettings(classicSystem).withWebsocketSettings(
            ClientConnectionSettings(classicSystem).websocketSettings
              .withPeriodicKeepAliveMaxIdle(1 minutes)
          )
        )

        val (
          sender: ActorRef[Sender.Protocol],
          wsOutgoing: Source[TextMessage.Strict, NotUsed]
        ) = Sender.create(
          serviceSettings.outgoingBufferSize,
          serviceSettings.outgoingOverflowStrategy
        )

        val webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
          Http().webSocketClientFlow(
            request = WebSocketRequest(websocketUrl),
            settings = wsSettings
          )

        val selfAdapter: ActorRef[WebsocketMessage] =
          ctx.messageAdapter[WebsocketMessage](IncomingMessage(_))

        val ((upgradeResponse, killSwitch), closed) = wsOutgoing
          .viaMat(webSocketFlow)(
            Keep.right
          ) // keep the materialized Future[WebSocketUpgradeResponse]
          .viaMat(KillSwitches.single)(Keep.both)
          .toMat(HookFlow.apply(selfAdapter))(
            Keep.both
          ) // also keep the Future[Done]
          .run()

        ctx.pipeToSelf(upgradeResponse) {
          case Success(upgrade) if upgrade.response.status == StatusCodes.SwitchingProtocols =>
            Connected
          case Success(upgrade) =>
            ConnectionError(UpgradeFailedException(websocketUrl))
          case Failure(ex) =>
            ConnectionError(ex)
        }
        initial(killSwitch, sender, botToken, serviceSettings)
      //listening(killSwitch, sender)
      }
    }
    // TODO: disconnect first inorder to avoid double connections
    .onFailure(SupervisorStrategy.restartWithBackoff(1.second, 5.second, 0.2))

  private def initial(
      killSwitch: KillSwitch,
      sender: ActorRef[Sender.Protocol],
      botToken: String,
      serviceSettings: ServiceSettings
  ): Behavior[BotCommand] = Behaviors.setup { ctx =>
    Behaviors.withStash(100) { stash =>
      Behaviors.receiveMessage {
        case Connected =>
          val pingSchedule = ctx.system.scheduler.scheduleAtFixedRate((serviceSettings.pingMaxTimeout/2).toJava, (serviceSettings.pingMaxTimeout/2).toJava, () => ctx.self ! Ping, ctx.executionContext)
          ctx.log.info("WS connection established, authenticating")
          val initState = ActorState(
            killSwitch = killSwitch,
            sender = sender,
            botToken = botToken,
            hooks = Seq.empty,
            internalSeq = 1,
            serviceSettings = serviceSettings,
            lastReply = LocalDateTime.now(),
            pingSchedule = pingSchedule
          )
          stash.unstashAll(authenticating(initState.pinged))

        case ConnectionError(t) =>
          ctx.log.error(s"Error connecting to WS service, shutting down...", t)
          Behaviors.stopped

        case other =>
          stash.stash(other)
          Behaviors.same

      }

    }

  }

  private def authenticating(
      state: ActorState
  ): Behavior[BotCommand] = Behaviors.setup[BotCommand] { ctx =>
    ctx.log.info(s"Authenticating...")
    state.sender ! ActorMessage(
      state.internalSeq,
      "authentication_challenge",
      Map("token" -> state.botToken)
    )
    Behaviors.withStash(100) { stash =>
      Behaviors.receiveMessage {
        case IncomingMessage(WebsocketEvent("hello", _, _, d)) =>
          ctx.log.info(s"Successfully authenticated! ${d}")
          stash.unstashAll(
            listening(state.pinged.sent)
          )
        case other =>
          stash.stash(other)
          Behaviors.same
      }

    }
  }

  private def listening(
      state: ActorState
  ): Behavior[BotCommand] = Behaviors
    .receive[BotCommand] { (ctx, cmd) =>
      cmd match {
        case DeregisterHook(hook) =>
          val filtered = state.hooks.filter(_ != hook)
          if (filtered.length == state.hooks.length) {
            logger.warn(s"Unhooking actor $hook which was never hooked")
          }
          ctx.log.debug(s"Dergistered hook for actor $hook")
          listening(state)
        case RegisterHook(hook) =>
          if (state.hooks.contains(hook)) {
            logger.warn(
              "Double registered actor (setup behavior called again?)"
            )
            Behaviors.same
          } else {
            ctx.log.debug(s"Registered hook for actor $hook")
            listening(state = state.copy(hooks = state.hooks :+ hook))
          }

        case IncomingMessage(WebsocketResponse(m, l)) =>
          state.hooks.foreach(_ ! WebsocketResponse(m,l))
          listening(state.pinged)


        case IncomingMessage(WebsocketEvent("authentication_challenge", _, _, _)) =>
          authenticating(state)

        case IncomingMessage(e : WebsocketEvent) =>
          state.hooks.foreach( _ ! e)
          listening(state)


        case Ping =>
          val delta = Duration.between(state.lastReply, LocalDateTime.now()).toScala
          if (delta > state.serviceSettings.pingMaxTimeout) {
            throw new RuntimeException(s"WS Timeout after ${delta.toMillis} ms")
          } else {
            // doesn't matter the payload
            state.sender ! ActorMessage(state.internalSeq, "ping", Map.empty)
            listening(state.sent)
          }

        case ConnectionClosed =>
          ctx.log.error(s"Websocket connection closed. Shutting down...")
          Behaviors.stopped

        case other =>
          ctx.log.error(s"Unsure how to handle $other")
          Behaviors.same
      }

    }
    .receiveSignal {

      case (ctx, PreRestart) =>
        ctx.log.warn("Restarting ... here we go again")

        //persist hooks
        state.hooks.map(RegisterHook).foreach( ctx.self ! _)
        state.pingSchedule.cancel()
        state.killSwitch.shutdown()
        Behaviors.same
      case (ctx, PostStop) =>
        ctx.log.warn("Received PostStop signal, stopping websockets...")

        state.pingSchedule.cancel()
        state.killSwitch.shutdown()
        Behaviors.stopped
    }

  case class UpgradeFailedException(websocketUrl: String) extends RuntimeException(s"Websocket upgrade failed at $websocketUrl")

  /* SERVICE SETTINGS */
  case class ServiceSettings(
      receiveParallelism: Int = 10,
      outgoingBufferSize: Int = 256,
      outgoingOverflowStrategy: OverflowStrategy = OverflowStrategy.fail,
      hookExecutionContext: ExecutionContext = ExecutionContext.global,
      pingMaxTimeout: FiniteDuration = 10.seconds
  )

}
