package ru.stachek66.tools

import java.io.{File, FileInputStream}
import java.nio.charset.StandardCharsets

import org.apache.commons.io.IOUtils
import org.scalatest.funsuite.AnyFunSuite

class TarGzTest extends AnyFunSuite {

  test("tgz-test: round-trip a single-entry tar.gz archive") {
    val src = new File("src/test/resources/test.txt")
    val out = File.createTempFile("mystem-scala-tgz-test-", ".out")
    out.deleteOnExit()

    TarGz.unpack(new File("src/test/resources/test.tar.gz"), out)

    val unpacked = IOUtils.toString(new FileInputStream(out), StandardCharsets.UTF_8)
    val original = IOUtils.toString(new FileInputStream(src), StandardCharsets.UTF_8)
    assert(unpacked.trim === original.trim)
  }
}
