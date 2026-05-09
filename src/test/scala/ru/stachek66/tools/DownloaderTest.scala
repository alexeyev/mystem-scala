package ru.stachek66.tools

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.apache.commons.io.FileUtils
import org.scalatest.funsuite.AnyFunSuite

/** Tests for [[Downloader]] using `file://` URLs so we don't depend on
  * mystem's CDN — the network is exactly the variability we want to
  * remove from CI. The contract being tested is the same regardless of
  * URL scheme (since `Downloader` just delegates to commons-io's
  * URL-to-file copier).
  */
class DownloaderTest extends AnyFunSuite {

  /** Create a temp file and write the given content to it; return the URL. */
  private def fileUrl(content: String): java.net.URL = {
    val f = File.createTempFile("downloader-source-", ".bin")
    f.deleteOnExit()
    Files.write(f.toPath, content.getBytes(StandardCharsets.UTF_8))
    f.toURI.toURL
  }

  test("downloadBinaryFile copies the URL contents to the destination byte-for-byte") {
    val src = fileUrl("hello, мир!")
    val dst = File.createTempFile("downloader-dst-", ".bin")
    dst.deleteOnExit()

    val returned = Downloader.downloadBinaryFile(src, dst)
    assert(returned === dst, "downloadBinaryFile should return its destination File")

    val written = FileUtils.readFileToString(dst, StandardCharsets.UTF_8)
    assert(written === "hello, мир!")
  }

  test("downloadBinaryFile creates missing parent directories of the destination") {
    // Why: the production caller writes to `~/.local/bin/mystem`, which
    // typically exists, but custom destinations (or first-run on a fresh
    // user account) don't. The wrapper should not require the user to
    // pre-create the directory — that's why `mkdirs()` is in
    // Downloader and not in the caller.
    val src = fileUrl("body")

    // Build a destination with TWO levels of missing parent directories.
    val tmpRoot = Files.createTempDirectory("downloader-test-")
    val deep = tmpRoot.resolve("does-not-exist-yet/and-this-too/output.bin").toFile
    assert(!deep.getParentFile.exists(), "test setup: parent must not exist before the call")

    val returned = Downloader.downloadBinaryFile(src, deep)
    assert(returned.exists(), "destination should exist after downloadBinaryFile returns")
    assert(returned.getParentFile.exists(), "parent directory should have been created")
    assert(FileUtils.readFileToString(deep, StandardCharsets.UTF_8) === "body")

    // Best-effort cleanup so test runs don't pile up tmp dirs.
    val _ = deep.delete()
    val _2 = deep.getParentFile.delete()
    val _3 = deep.getParentFile.getParentFile.delete()
    val _4 = tmpRoot.toFile.delete()
  }

  test("downloadBinaryFile is idempotent — calling it twice overwrites") {
    // If a previous run left a stale binary at the destination, the next
    // run should freshen it rather than refuse. That's how Factory's
    // version-mismatch path works (delete + re-download).
    val src1 = fileUrl("first run")
    val src2 = fileUrl("second run")
    val dst = File.createTempFile("downloader-overwrite-", ".bin")
    dst.deleteOnExit()

    Downloader.downloadBinaryFile(src1, dst)
    assert(FileUtils.readFileToString(dst, StandardCharsets.UTF_8) === "first run")

    Downloader.downloadBinaryFile(src2, dst)
    assert(FileUtils.readFileToString(dst, StandardCharsets.UTF_8) === "second run")
  }

  test("downloadBinaryFile surfaces a recognisable error when the URL is unreachable") {
    // `file:///nonexistent/...` raises FileNotFoundException via
    // commons-io. We don't insist on the exact exception type — different
    // commons-io versions wrap it differently — but we DO insist that the
    // failure isn't silent and the destination wasn't half-created.
    val src = new java.net.URI("file:///definitely/not/a/real/path/abcxyz123.bin").toURL
    val dst = File.createTempFile("downloader-bad-source-", ".bin")
    val _ = dst.delete() // start with destination NOT existing
    dst.deleteOnExit()

    val ex = intercept[Throwable](Downloader.downloadBinaryFile(src, dst))
    val msg = ex.getMessage
    assert(msg !== null, s"unreachable-source exception must have a message; class=${ex.getClass.getName}")
    assert(!dst.exists() || dst.length() === 0L, "no half-written destination on failure")
  }
}
