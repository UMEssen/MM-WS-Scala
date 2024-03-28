package de.ship.mattermost.ws

import de.ship.mattermost.ws.model.core._
import akka.actor.typed.ActorRef

object Protocol {
  trait BotCommand
  case class RegisterHook(actor: ActorRef[WebsocketMessage])   extends BotCommand
  case class DeregisterHook(actor: ActorRef[WebsocketMessage]) extends BotCommand
}
