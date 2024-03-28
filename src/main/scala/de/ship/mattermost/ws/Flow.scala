package de.ship.mattermost.ws

import de.ship.mattermost.ws.WSJsonProtocol._
import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.LazyLogging
import spray.json._
import de.ship.mattermost.ws.model.core._


import scala.util.{Failure, Success, Try}
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.Flow

object HookFlow extends LazyLogging {
  private def deserializeFlow: Flow[Message, Try[WebsocketMessage], NotUsed] =
    Flow[Message]
      .map { m =>
        Try {
          m match {
            case m: TextMessage.Strict =>
              val json = m.text.parseJson.asJsObject
              json match {
                case ast if ast.fields.contains("seq_reply") =>
                  ast.convertTo[WebsocketResponse]
                case ast if ast.fields.contains("event") =>
                  ast.convertTo[WebsocketEvent]
                case _ =>
                  throw new RuntimeException(
                    s"No idea how to handle: ${m.text}"
                  )
              }
            case other =>
              throw new MatterMostWsProtocolException(
                s"Unsupported message format: $other"
              )
          }
        }
      }

  def apply(
      respondTo: ActorRef[WebsocketMessage]
      //hookedActors: Seq[ActorRef[Protocol.WebsocketMessage]]
  ): Sink[Message, NotUsed] = {

    deserializeFlow
      .to(Sink.foreach[Try[WebsocketMessage]] {
        case Success(m) =>
          respondTo ! m
        case Failure(ex) =>
          logger.error("Could not handle Websocket message.", ex)
      })
  }
}
