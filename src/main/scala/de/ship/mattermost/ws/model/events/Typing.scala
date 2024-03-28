package de.ship.mattermost.ws.model.events

import de.ship.mattermost.ws.model.core._
import de.ship.mattermost.ws.WSJsonProtocol._
import spray.json._

object Typing extends EventParser("typing") {
  case class TypingData(
      user_id: String,
      parent_id: String
  ) extends WSEventData

  implicit val TypingDataFormat: RootJsonFormat[TypingData] = jsonFormat2(TypingData)
  def convert(js: JsValue): WSEventData                     = js.convertTo[TypingData]
}

