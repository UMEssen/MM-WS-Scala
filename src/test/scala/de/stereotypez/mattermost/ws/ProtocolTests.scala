
package de.ship.mattermost.ws

import de.ship.mattermost.ws.model.events._
import de.ship.mattermost.ws.model.core._
import de.ship.mattermost.ws.WSJsonProtocol._

import spray.json._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProtocolTests extends AnyFunSuite with Matchers {
  test("test01") {
    val txt = """{"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}"""
    txt.parseJson.convertTo[Broadcast]
  }

  
  test("test02")  {
    val txt = """{"event": "status_change", "data": {"status":"online","user_id":"4ac3xh974jgmffq8rdowxunjma"}, "broadcast": {"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}, "seq": 1}"""
    txt.parseJson.convertTo[WebsocketEvent]
  }

  
  test("test03") {
    val txt = """{"status":"online","user_id":"zt3rb4qfi7bm3gfk31c97a3fih"}"""
    val data = txt.parseJson.convertTo[StatusChange.StatusChangeData]
    data.status shouldBe "online"
    data.user_id shouldBe "zt3rb4qfi7bm3gfk31c97a3fih"
  }

  test("test04") {
    val txt = """{"event":"posted","data":{"channel_display_name":"@admin","channel_name":"4ac3xh974jgmffq8rdowxunjma__wb7dpg3tsfrimxcu8767ig55sh","channel_type":"D","mentions":"[\"4ac3xh974jgmffq8rdowxunjma\"]","post":"{\"id\":\"xo8gwpzsq78tur5soq47cjtskh\",\"create_at\":1633085267810,\"update_at\":1633085267810,\"edit_at\":0,\"delete_at\":0,\"is_pinned\":false,\"user_id\":\"wb7dpg3tsfrimxcu8767ig55sh\",\"channel_id\":\"3xqxgouhqjnm5k49qohjort7yc\",\"root_id\":\"\",\"parent_id\":\"\",\"original_id\":\"\",\"message\":\"oh there once was a hero named ragnar the red\",\"type\":\"\",\"props\":{\"disable_group_highlight\":true},\"hashtags\":\"\",\"pending_post_id\":\"wb7dpg3tsfrimxcu8767ig55sh:1633085267773\",\"reply_count\":0,\"last_reply_at\":0,\"participants\":null,\"is_following\":false,\"metadata\":{}}","sender_name":"@admin","set_online":true,"team_id":""},"broadcast":{"omit_users":null,"user_id":"","channel_id":"3xqxgouhqjnm5k49qohjort7yc","team_id":""},"seq":3}"""
    val event = txt.parseJson.convertTo[WebsocketEvent]
    event.data match {
      case data: Posted.PostData =>
        data.post.id shouldBe "xo8gwpzsq78tur5soq47cjtskh"
      case _=> assert(false)
    }
  }
}
