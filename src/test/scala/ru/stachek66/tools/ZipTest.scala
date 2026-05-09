package ru.stachek66.tools

import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.nio.charset.StandardCharsets

import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.apache.commons.io.IOUtils
import org.scalatest.funsuite.AnyFunSuite

/**
 * Behavioral spec for [[Zip]].
 *
 * Pinned contract:
 *   - `unpack` extracts the first entry of a zip archive and writes it to
 *     `dst`, returning `dst`.
 *   - `traditionalExtension` is `"zip"`.
 *   - When the archive has multiple entries, only the first is extracted —
 *     this is intentional because mystem ships single-binary archives.
 *   - Missing source files surface a Java I/O exception, not a silent
 *     empty-output file.
 */
class ZipTest extends AnyFunSuite {

  test("traditionalExtension is `zip`") {
    assert(Zip.traditionalExtension === "zip")
  }

  test("unpacking a single-entry zip restores the original file content byte-for-byte") {
    val src = new File("src/test/resources/test.txt")
    val out = File.createTempFile("mystem-scala-zip-test-", ".out")
    out.deleteOnExit()

    val returned = Zip.unpack(new File("src/test/resources/test.zip"), out)
    assert(returned === out, "unpack should return the destination File")

    val unpacked = readAllText(out)
    val original = readAllText(src)
    // Trim because of CR/LF line-separator differences between the
    // committed test fixture and the working copy.
    assert(unpacked.trim === original.trim)
  }

  test("unpacking a multi-entry archive extracts only the first entry") {
    // Single-binary releases are mystem's contract; we don't want a future
    // mystem build that ships extras to silently corrupt the executable.
    // This regression test asserts the truncate-after-one-entry behavior.
    val multi = makeMultiEntryZip(
      "first-entry"  -> "FIRST",
      "second-entry" -> "SECOND",
      "third-entry"  -> "THIRD"
    )
    val out = File.createTempFile("mystem-scala-zip-multi-", ".out")
    out.deleteOnExit()

    Zip.unpack(multi, out)

    val unpacked = readAllText(out)
    assert(unpacked === "FIRST", "first entry only — others must be ignored")
  }

  test("unpacking a missing source file raises an I/O exception") {
    // Concrete failure mode: passing a non-existent path. The previous
    // implementation propagated FileNotFoundException via stream creation;
    // we just want SOME I/O-class exception, not silence + empty output.
    val missing = new File("/nonexistent/this/should/never/exist.zip")
    val out = File.createTempFile("mystem-scala-zip-missing-", ".out")
    out.deleteOnExit()

    val ex = intercept[IOException] {
      Zip.unpack(missing, out)
    }
    val msg = ex.getMessage
    assert(msg !== null, "exception must have a message")
    assert(msg.nonEmpty, "the exception must explain itself")
  }

  // -- Helpers ------------------------------------------------------------

  private def readAllText(f: File): String = {
    val is = new FileInputStream(f)
    try IOUtils.toString(is, StandardCharsets.UTF_8)
    finally is.close()
  }

  /** Build a zip with N named entries and given content, return its File. */
  private def makeMultiEntryZip(entries: (String, String)*): File = {
    val zip = File.createTempFile("mystem-scala-zip-fixture-", ".zip")
    zip.deleteOnExit()
    val out = new ZipArchiveOutputStream(new FileOutputStream(zip))
    try {
      entries.foreach { case (name, body) =>
        val e = new ZipArchiveEntry(name)
        out.putArchiveEntry(e)
        out.write(body.getBytes(StandardCharsets.UTF_8))
        out.closeArchiveEntry()
      }
    } finally {
      out.finish()
      out.close()
    }
    zip
  }
}
