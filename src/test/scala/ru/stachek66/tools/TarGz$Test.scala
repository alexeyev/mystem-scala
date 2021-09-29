package ru.stachek66.tools

import java.io.{File, FileInputStream}

import org.apache.commons.io.IOUtils
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

/**
 * alexeyev 
 * 11.09.14.
 */
class TarGz$Test extends FunSuite {

  test("tgz-test") {
    val src = new File("src/test/resources/test.txt")
    TarGz.unpack(
      new File("src/test/resources/test.tar.gz"),
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
