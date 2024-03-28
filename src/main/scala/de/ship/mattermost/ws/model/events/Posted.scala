package de.ship.mattermost.ws.model.events

import de.ship.mattermost.ws.model.core._
import de.ship.mattermost.ws.WSJsonProtocol._
import spray.json._

object Posted extends EventParser(eventType = "posted") {
  case class Post(
      id: String,
      create_at: Long, //convert to a more usable format
      update_at: Long, //ditto
      edit_at: Long,   //ditto²
      is_pinned: Boolean,
      user_id: String,
      channel_id: String,
      root_id: String,
      parent_id: Option[String],
      original_id: String,
      message: String,
      `type`: String,
      props: Map[String, JsValue],
      hashtags: String,
      pending_post_id: String,
      reply_count: Long,
      last_reply_at: Long,
      //participants: String, //TODO
      is_following: Option[Boolean],
      metadata: Map[String, JsValue]
  )

  case class PostData(
      channel_display_name: String,
      channel_name: String,
      channel_type: String,
      post: Post,
      sender_name: String,
      set_online: Boolean,
      team_id: String
  ) extends WSEventData

  implicit val PostFormat: RootJsonFormat[Post] = jsonStringEncodedFormat(jsonFormat19(Post))
  implicit val PostDataFormat: RootJsonFormat[PostData] = jsonFormat7(PostData)
  def convert(js: JsValue): WSEventData = js.convertTo[PostData]
}
