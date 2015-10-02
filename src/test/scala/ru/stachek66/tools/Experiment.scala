package ru.stachek66.tools

import ru.stachek66.tools.external.{FailSafeExternalProcessServer, ExternalProcessServer}

/**
 * It does fcking work!
 * alexeyev 
 * 14.09.14.
 */
object Experiment extends App {

  val thread = new FailSafeExternalProcessServer("/home/alexeyev/mystem -igd --eng-gr --format json ")

  for (i <- List("красота", "спасёт", "человечество", "достоевский", "михайлович", "ip"))
    println(thread.syncRequest(i))

  println(thread.syncRequest("я тебя по ip вычеслю"))
}
