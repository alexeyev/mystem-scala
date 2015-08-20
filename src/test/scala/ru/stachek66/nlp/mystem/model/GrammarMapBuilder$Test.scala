package ru.stachek66.nlp.mystem.model

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

/**
 * alexeyev 
 * 16.09.14.
 */
class GrammarMapBuilder$Test extends FunSuite {

  test("grammar") {
    println(GrammarMapBuilder.tagToEnumMap("ADV").withName("ADV"))
  }

}
