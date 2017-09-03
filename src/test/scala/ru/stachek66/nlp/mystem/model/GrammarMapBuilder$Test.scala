package ru.stachek66.nlp.mystem.model

import org.scalatest.FunSuite

/**
 * alexeyev 
 * 16.09.14.
 */
class GrammarMapBuilder$Test extends FunSuite {

  test("grammar") {
    println(GrammarMapBuilder.tagToEnumMap("ADV").withName("ADV"))
  }

}
