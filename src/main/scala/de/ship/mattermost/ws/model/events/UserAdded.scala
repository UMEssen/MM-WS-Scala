package de.ship.mattermost.ws.model.events

import de.ship.mattermost.ws.model.core._
import de.ship.mattermost.ws.WSJsonProtocol._
import spray.json._

object UserAdded extends EventParser("user_added") {
  case class UserAddedData(
      team_id: String,
      user_id: String
  ) extends WSEventData

  implicit val userRemovedDataFormat: RootJsonFormat[UserAddedData] = jsonFormat2(UserAddedData)
  def convert(js: JsValue): WSEventData                             = js.convertTo[UserAddedData]
}

