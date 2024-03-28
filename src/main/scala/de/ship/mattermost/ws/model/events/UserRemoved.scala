package de.ship.mattermost.ws.model.events

import de.ship.mattermost.ws.model.core._
import de.ship.mattermost.ws.WSJsonProtocol._
import spray.json._

object UserRemoved extends EventParser("user_removed") {
  case class UserRemovedData(
      remover_id: String,
      channel_id: Option[String],
      user_id: Option[String]
  ) extends WSEventData

  implicit val userRemovedDataFormat: RootJsonFormat[UserRemovedData] = jsonFormat3(UserRemovedData)
  def convert(js: JsValue): WSEventData                               = js.convertTo[UserRemovedData]
}
