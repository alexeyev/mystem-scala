package ru.stachek66.tools

import java.io.{File, FileInputStream}
import java.nio.charset.Charset

import org.apache.commons.io.IOUtils
import org.scalatest.FunSuite

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
        val content0 = IOUtils.toString(new FileInputStream(f), Charset.defaultCharset)
        val content1 = IOUtils.toString(new FileInputStream(src), Charset.defaultCharset)
        print(content0.trim + " vs " + content1.trim)
        assert(content0 === content1)
    }
  }

}
