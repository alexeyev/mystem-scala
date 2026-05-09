package ru.stachek66.tools

import java.io.{File, FileInputStream}
import java.nio.charset.StandardCharsets

import org.apache.commons.io.IOUtils
import org.scalatest.funsuite.AnyFunSuite

class ZipTest extends AnyFunSuite {

  test("zip-test: round-trip a single-entry zip archive") {
    val src = new File("src/test/resources/test.txt")
    val out = File.createTempFile("mystem-scala-zip-test-", ".out")
    out.deleteOnExit()

    Zip.unpack(new File("src/test/resources/test.zip"), out)

    val unpacked = IOUtils.toString(new FileInputStream(out), StandardCharsets.UTF_8)
    val original = IOUtils.toString(new FileInputStream(src), StandardCharsets.UTF_8)
    // Trim because of CR/LF line-separator differences.
    assert(unpacked.trim === original.trim)
  }
}
