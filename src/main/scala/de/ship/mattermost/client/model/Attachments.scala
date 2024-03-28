package de.ship.mattermost.client.model

import java.util
import scala.jdk.CollectionConverters._


trait Action {
  def forMM: util.Map[String, AnyRef]
}

case class ActionButton(name: String, url: String, context: Map[String, String]) extends Action {
  override def forMM: util.Map[String, AnyRef] = {
    Map(
      "name" -> name,
      "integration" -> Map(
        "url" -> url,
        "context" -> context.map(t => (t._1, t._2.asInstanceOf[AnyRef])).asJava
      ).asJava
    ).asJava
  }
}

case class ActionSelect(name: String, url: String, context: Map[String, String], options: Seq[Tuple2[String, String]]) extends Action {
  override def forMM: util.Map[String, AnyRef] = {
    Map(
      "name" -> name,
      "integration" -> Map(
        "url" -> url,
        "context" -> context.map(t => (t._1, t._2.asInstanceOf[AnyRef])).asJava
      ).asJava,
      "type" -> "select",
      "options" -> options.map {
        case (key, value) =>
          Map[String, AnyRef]("text" -> key, "value" -> value).asJava
      }.asJava
    ).asJava
  }
}

case class Actions(actions: Seq[Action]) {
  def forMM: util.List[util.Map[String, AnyRef]] = actions.map(_.forMM).asJava
}

case class Attachment(params: Option[Map[String, String]] = None, actions: Option[Actions] = None) {

  def add(action: Action): Attachment = {
    Attachment(
      params,
      Some(
        actions
          .map(actions => Actions(actions.actions :+ action))
          .getOrElse(Actions(Seq(action)))
      )
    )
  }

  def forMM: util.Map[String, AnyRef] = {
    ((params, actions) match {
      case (Some(p), Some(a)) =>
        p.map(t => (t._1, t._2.asInstanceOf[AnyRef])) ++ Map("actions" -> a.forMM.asInstanceOf[AnyRef])

      case (Some(p), None) =>
        p.map(t => (t._1, t._2.asInstanceOf[AnyRef]))

      case (None, Some(a)) =>
        Map("actions" -> a.forMM.asInstanceOf[AnyRef])

      case _ => Map.empty[String, AnyRef]
    }).asJava
  }
}

case class Attachments(attachments: Seq[Attachment] = Seq.empty) {
  def add(attachment: Attachment): Attachments = Attachments(attachments :+ attachment)

  def forMM: util.List[util.Map[String, AnyRef]] = {
    attachments.map(_.forMM).asJava
  }
}
