package de.ship.mattermost.ws

import de.ship.mattermost.ws.model.core._
import de.ship.mattermost.ws.model.events
import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat
import spray.json._

trait WSJsonProtocol extends DefaultJsonProtocol
object WSJsonProtocol extends DefaultJsonProtocol {

  implicit val WSResponseFormat: RootJsonFormat[WebsocketResponse] = jsonFormat2(WebsocketResponse)
  abstract class EventParser(val eventType: String) {
    def convert(js: JsValue): WSEventData
  }

  def jsonStringEncodedFormat[T](format: RootJsonFormat[T]): RootJsonFormat[T] = new RootJsonFormat[T] {
    def write(obj: T): JsValue = format.write(obj).compactPrint.toJson
    def read(json: JsValue): T = json match {
      case JsString(value) =>
        format.read(value.parseJson)

      // TODO: this design could potentially lead to unexpected errors!!!
      // perhaps make it more explicit to the user?
      // the idea is, that you have a single format that
      // doubles for the string and the regular format
      case identity: JsObject => format.read(identity)

      case other =>
        throw new RuntimeException(s"not string encoded json: ${other}")
    }

  }

  //TODO: perhaps retrieve this via reflection?
  val parsers: List[EventParser] = List(
    events.Posted,
    events.Typing,
    events.UserRemoved,
    events.StatusChange,
    events.UserAdded
  )

  implicit val broadCastFormat: RootJsonFormat[Broadcast] = jsonFormat4(Broadcast)

  object JsonProtocol extends DefaultJsonProtocol

  implicit object WSEventFormat extends RootJsonFormat[WebsocketEvent] {
    def read(json: JsValue): WebsocketEvent = {
      val fields   = json.asJsObject.fields
      val event    = fields("event").convertTo[String]
      val jsonData = fields("data")
      val data     = parsers.find(parser => parser.eventType == event).flatMap(parser => Some(parser.convert(jsonData))).getOrElse(FallbackData(jsonData.asJsObject))
      WebsocketEvent(
        event = event,
        seq = fields("seq").convertTo[Long],
        data = data,
        broadcast = fields("broadcast").convertTo[Broadcast]
      )
    }

    def write(obj: WebsocketEvent): JsValue = ???

  }

}
