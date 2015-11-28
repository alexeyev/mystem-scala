package ru.stachek66.nlp.mystem.holding

/**
 * alexeyev 
 * 12.09.14.
 */
object HolderApp extends App {

  val h = new Factory()
  println("holder ready")
  val p = h.newMyStem("3.0").get
  println("raw process created")

  while (true) {
    println("asking")
    println(p.analyze(Request("леново")))
    println("answer printed")
    Thread.sleep(math.round(math.random * 10000))
  }
}