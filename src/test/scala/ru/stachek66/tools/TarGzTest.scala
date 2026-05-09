package ru.stachek66.tools

import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.io.IOUtils
import org.scalatest.funsuite.AnyFunSuite

/**
 * Behavioral spec for [[TarGz]]. Mirror of [[ZipTest]]; same contract.
 */
class TarGzTest extends AnyFunSuite {

  test("traditionalExtension is `tar.gz`") {
    assert(TarGz.traditionalExtension === "tar.gz")
  }

  test("unpacking a single-entry tar.gz restores the original file content") {
    val src = new File("src/test/resources/test.txt")
    val out = File.createTempFile("mystem-scala-tgz-test-", ".out")
    out.deleteOnExit()

    val returned = TarGz.unpack(new File("src/test/resources/test.tar.gz"), out)
    assert(returned === out, "unpack should return the destination File")

    assert(readAllText(out).trim === readAllText(src).trim)
  }

  test("unpacking a multi-entry archive extracts only the first entry") {
    val multi = makeMultiEntryTarGz(
      "first-entry"  -> "FIRST",
      "second-entry" -> "SECOND"
    )
    val out = File.createTempFile("mystem-scala-tgz-multi-", ".out")
    out.deleteOnExit()

    TarGz.unpack(multi, out)
    assert(readAllText(out) === "FIRST")
  }

  test("unpacking a missing source file raises an I/O exception") {
    val missing = new File("/nonexistent/this/should/never/exist.tar.gz")
    val out = File.createTempFile("mystem-scala-tgz-missing-", ".out")
    out.deleteOnExit()

    val ex = intercept[IOException](TarGz.unpack(missing, out))
    val msg = ex.getMessage
    assert(msg !== null)
    assert(msg.nonEmpty)
  }

  // -- Helpers ------------------------------------------------------------

  private def readAllText(f: File): String = {
    val is = new FileInputStream(f)
    try IOUtils.toString(is, StandardCharsets.UTF_8)
    finally is.close()
  }

  private def makeMultiEntryTarGz(entries: (String, String)*): File = {
    val tgz = File.createTempFile("mystem-scala-tgz-fixture-", ".tar.gz")
    tgz.deleteOnExit()
    val gzip = new GZIPOutputStream(new FileOutputStream(tgz))
    val tar = new TarArchiveOutputStream(gzip)
    try {
      entries.foreach { case (name, body) =>
        val bytes = body.getBytes(StandardCharsets.UTF_8)
        val e = new TarArchiveEntry(name)
        e.setSize(bytes.length.toLong)
        tar.putArchiveEntry(e)
        tar.write(bytes)
        tar.closeArchiveEntry()
      }
    } finally {
      tar.finish()
      tar.close()
      gzip.close()
    }
    tgz
  }
}
