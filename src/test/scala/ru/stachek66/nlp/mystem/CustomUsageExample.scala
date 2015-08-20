package ru.stachek66.nlp.mystem

import ru.stachek66.nlp.mystem.holding.{Request, Factory}

object CustomUsageExample extends App {

  val mystemInstance = new Factory("-id --format json").newMyStem("3.0")

  val texts =
    List(
      "Шалтай-болтай висел на стене",
      "Шалтай-болтай свалился во сне",
      "Вся королевская конница",
      "Вся королевская рать"
    )

  texts.foreach {
    case text =>
      mystemInstance.analyze(Request(text)).info.foreach {
        case info =>
          println(info.initial, info.lex, info.weight)
      }
  }
}
