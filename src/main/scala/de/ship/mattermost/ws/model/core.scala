package de.ship.mattermost.ws.model

import spray.json._

package core {
  sealed trait WebsocketMessage
  trait WSEventData

  case class WebsocketResponse(
      status: String,
      seq_reply: Long
  ) extends WebsocketMessage

  case class WebsocketEvent(
      event: String,
      seq: Long,
      broadcast: Broadcast,
      data: WSEventData
  ) extends WebsocketMessage

  case class Broadcast(
      omit_users: Option[JsValue],
      user_id: String,
      channel_id: String,
      team_id: String
  )

  //incase an event is either unimplemented or unknown in general
  case class FallbackData(
      json: JsObject
  ) extends WSEventData
}
