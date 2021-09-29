package ru.stachek66.tools

import java.io.{File, FileInputStream}

import org.apache.commons.io.IOUtils
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import org.junit.runner.RunWith

/**
 * alexeyev 
 * 11.09.14.
 */
class Zip$Test extends FunSuite {

  test("zip-test") {
    val src = new File("src/test/resources/test.txt")
    Zip.unpack(
      new File("src/test/resources/test.zip"),
      new File("src/test/resources/res.txt")) match {
      case f =>
        val content0 = IOUtils.toString(new FileInputStream(f))
        val content1 = IOUtils.toString(new FileInputStream(src))
        print(content0.trim + " vs " + content1.trim)
        // trimming thanks to line separators; should be more careful maybe
        assert(content0.trim === content1.trim)
    }
  }

}
