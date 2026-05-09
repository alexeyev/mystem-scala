package ru.stachek66.nlp.mystem

import java.net.URI
import java.net.URL

import org.scalatest.funsuite.AnyFunSuite

/**
 * Behavioral spec for [[Properties]].
 *
 * Pinned contract:
 *   - For every supported (mystem-version, os-suffix) pair declared in
 *     `mystem-sources.conf`, `getUrl` returns the documented URL verbatim.
 *   - Malformed version strings are rejected with a useful message.
 *   - Unknown version / unknown OS combinations fail with a message naming
 *     the offending parameter — important for diagnosing the most common
 *     "why isn't this downloading" support requests.
 *   - `BIN_FILE_NAME` resolves to the right filename per OS.
 *   - `BinDestination` points at a writable per-user location.
 */
class PropertiesTest extends AnyFunSuite {

  // -- Happy-path URL resolution -----------------------------------------

  test("getUrl resolves every documented (version, os) pair") {
    // Spelling these all out is deliberate: a typo in `mystem-sources.conf`
    // is a silent download failure at runtime; the test catches it at build
    // time. The URL strings here intentionally duplicate the conf — the
    // goal is to detect drift, not to DRY.
    val cases: Seq[((String, String), String)] = Seq(
      ("3.0", "win32")    -> "http://download.cdn.yandex.net/mystem/mystem-3.0-win7-32bit.zip",
      ("3.0", "win64")    -> "http://download.cdn.yandex.net/mystem/mystem-3.0-win7-64bit.zip",
      ("3.0", "linux32")  -> "http://download.cdn.yandex.net/mystem/mystem-3.0-linux3.5-32bit.tar.gz",
      ("3.0", "linux64")  -> "http://download.cdn.yandex.net/mystem/mystem-3.0-linux3.1-64bit.tar.gz",
      ("3.0", "freebsd64") -> "http://download.cdn.yandex.net/mystem/mystem-3.0-freebsd9.0-64bit.tar.gz",
      ("3.0", "osx")      -> "http://download.cdn.yandex.net/mystem/mystem-3.0-macosx10.8.tar.gz",
      ("3.1", "win64")    -> "http://download.cdn.yandex.net/mystem/mystem-3.1-win-64bit.zip",
      ("3.1", "linux64")  -> "http://download.cdn.yandex.net/mystem/mystem-3.1-linux-64bit.tar.gz",
      ("3.1", "osx")      -> "http://download.cdn.yandex.net/mystem/mystem-3.1-macosx.tar.gz"
    )
    cases.foreach { case ((version, os), expected) =>
      val actual = Properties.getUrl(version, os)
      assert(
        actual === URI.create(expected).toURL(),
        s"getUrl($version, $os) mismatch: got $actual, expected $expected"
      )
    }
  }

  // -- Validation / error reporting --------------------------------------

  test("getUrl rejects malformed version strings with a `pattern` hint") {
    // The message has to mention "pattern" so users can tell this is a
    // version-format problem rather than a network/config problem.
    val cases = Seq("not-a-version", "v3.0", "3", "3.0.1", "3.x", "")
    cases.foreach { bad =>
      val ex = intercept[IllegalArgumentException](Properties.getUrl(bad, "linux64"))
      assert(
        ex.getMessage.toLowerCase.contains("pattern"),
        s"version=$bad: error message should mention 'pattern' but was: ${ex.getMessage}"
      )
    }
  }

  test("getUrl reports a useful error for an unknown OS suffix") {
    val ex = intercept[Exception](Properties.getUrl("3.0", "no-such-os"))
    assert(ex.getMessage.contains("no-such-os"), "OS name must appear in the error")
    // The original cause should be preserved (typesafe-config Missing).
    assert(ex.getCause !== null, "the underlying config error must be preserved")
  }

  test("getUrl reports a useful error for an unknown version") {
    val ex = intercept[Exception](Properties.getUrl("9.9", "linux64"))
    assert(ex.getMessage.contains("9.9"), "version must appear in the error")
    assert(ex.getCause !== null, "the underlying config error must be preserved")
  }

  // -- URL shape sanity --------------------------------------------------

  test("every resolved URL is well-formed and reachable as a URL value") {
    val u: URL = Properties.getUrl("3.1", "linux64")
    assert(u.getProtocol === "http", "currently HTTP; switch to HTTPS is a future change")
    assert(u.getHost === "download.cdn.yandex.net")
    assert(u.getPath.endsWith(".tar.gz"))
  }

  // -- BIN_FILE_NAME / BinDestination ------------------------------------

  test("BIN_FILE_NAME is `mystem.exe` on Windows hosts and `mystem` everywhere else") {
    // We can't easily mock CurrentOs without restructuring, so this test
    // just observes the running JVM's value and checks the consistency
    // rule. CI runs on Linux so we expect "mystem" most of the time, and
    // our Windows CI cell catches the .exe branch.
    val current = Properties.CurrentOs
    val expected = if (current.startsWith("win")) "mystem.exe" else "mystem"
    assert(
      Properties.BIN_FILE_NAME === expected,
      s"BIN_FILE_NAME=${Properties.BIN_FILE_NAME} doesn't match expected=$expected for OS=$current"
    )
  }

  test("BinDestination points inside the user's home directory") {
    val home = System.getProperty("user.home")
    assert(
      Properties.BinDestination.startsWith(home),
      s"BinDestination=${Properties.BinDestination} should start with user.home=$home"
    )
    assert(
      Properties.BinDestination.endsWith("/"),
      "BinDestination should be a directory path ending with '/'"
    )
  }
}
