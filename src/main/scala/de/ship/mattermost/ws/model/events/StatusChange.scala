package de.ship.mattermost.ws.model.events

import de.ship.mattermost.ws.model.core._
import de.ship.mattermost.ws.WSJsonProtocol._
import spray.json._

object StatusChange extends EventParser("status_change") {
  case class StatusChangeData(
      status: String,
      user_id: String
  ) extends WSEventData

  implicit val statusChangeFormat: RootJsonFormat[StatusChangeData] = jsonFormat2(StatusChangeData)
  def convert(js: JsValue): WSEventData = js.convertTo[StatusChangeData]
}
