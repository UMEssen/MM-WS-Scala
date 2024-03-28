package de.ship.mattermost.client.util

object Markdown {

  /**
   * Render a list of case classes to markdown table.
   *
   * @param headerNames Optional names for table header row. If not set the product element names will be taken.
   * @param t           List of case classes.
   * @return
   */
  def toTable(headerNames: Option[Seq[String]] = None)(t: Seq[Product]): String = {
    t.headOption
      .map(m => {
        Seq(
          headerNames
            .map(names => {
              assert(names.length == m.productElementNames.length)
              names
            })
            .getOrElse(m.productElementNames)
            .mkString("|", "|", "|"),
          m.productIterator
            .map {
              case _: Numeric[_] => "---------------:"
              case _: Option[Numeric[_]] => "---------------:"
              case _ => ":---------------"
            }
            .mkString("|", "|", "|"),
          t.map(_.productIterator
            .map {
              case v: Option[_] => v.getOrElse("")
              case v => v
            }
            .mkString("|", "|", "|"))
            .mkString("\n")
        ).mkString("\n")
      })
      .getOrElse("")
  }

}
