package ru.stachek66.nlp.mystem

import org.scalatest.funsuite.AnyFunSuite

/**
 * Behavioral spec for the `(os.name, os.arch)` → `mystem-sources.conf` suffix
 * mapping.
 *
 * The mapping has to be defensive: the JVM reports `os.name` with arbitrary
 * vendor-decided strings ("Windows 10", "Mac OS X", "Darwin", "Linux", and
 * the long tail), and any wrong fallback hits the user with the wrong
 * download URL. We pin the table here so any future change is intentional.
 */
class OsDetectionTest extends AnyFunSuite {

  // -- Windows ------------------------------------------------------------

  test("modern Windows (with a space in os.name) is recognized as Windows") {
    // This is the regression I'm guarding: the previous implementation
    // matched the literal "Windows7" and silently fell back to macOS for
    // every Windows 10 / 11 host. Anyone running mvn test on Windows would
    // download the wrong binary and have no idea why mystem segfaults.
    assert(os("Windows 10", "amd64") === "win64")
    assert(os("Windows 11", "x86_64") === "win64")
    assert(os("Windows Server 2022", "amd64") === "win64")
    assert(os("Windows 7", "x86") === "win32")
  }

  test("Windows detection is case-insensitive on os.name") {
    // `System.getProperty("os.name")` is canonical-cased in the JVM, but
    // a user setting -Dos.name explicitly might pass any case. Cheap to
    // be tolerant.
    assert(os("WINDOWS 10", "amd64") === "win64")
    assert(os("windows 10", "amd64") === "win64")
    assert(os("WiNdOwS 10", "amd64") === "win64")
  }

  test("Windows arch detection: amd64, x86_64, x86, i386 all classified correctly") {
    assert(os("Windows 10", "amd64") === "win64")
    assert(os("Windows 10", "x86_64") === "win64")
    assert(os("Windows 10", "AMD64") === "win64")
    // 32-bit JVM on 64-bit Windows reports x86 — we follow the JVM's bitness.
    assert(os("Windows 10", "x86") === "win32")
    assert(os("Windows 7", "i386") === "win32")
  }

  // -- Linux ---------------------------------------------------------------

  test("Linux is recognized in every common arch spelling") {
    assert(os("Linux", "amd64") === "linux64")
    assert(os("Linux", "x86_64") === "linux64")
    assert(os("Linux", "aarch64") === "linux64") // ARM64 still maps to linux64 release
    assert(os("Linux", "x86") === "linux32")
    assert(os("Linux", "i686") === "linux32")
  }

  // -- macOS ---------------------------------------------------------------

  test("macOS is recognized under all known os.name spellings") {
    // The JVM has reported macOS as "Mac OS X" historically and "Mac OS"
    // on newer JDKs. Some Apple Silicon JDKs still report "Mac OS X".
    assert(os("Mac OS X", "x86_64") === "osx")
    assert(os("Mac OS", "aarch64") === "osx")
    assert(os("macOS", "arm64") === "osx")
    // Darwin (i.e., the kernel name) — never reported by HotSpot, but
    // some embedded JVMs do. Costs nothing to handle.
    assert(os("Darwin", "arm64") === "osx")
  }

  // -- FreeBSD -------------------------------------------------------------

  test("FreeBSD is recognized") {
    assert(os("FreeBSD", "amd64") === "freebsd64")
    assert(os("freebsd", "x86_64") === "freebsd64")
  }

  // -- Fallback ------------------------------------------------------------

  test("unknown OS falls back to the macOS suffix (with a warning)") {
    // We don't promise this fallback forever — it's the legacy behavior of
    // the original code. Pinning it forces a future change to be intentional.
    assert(os("Plan9", "x86_64") === "osx")
    assert(os("Solaris", "sparcv9") === "osx")
    assert(os("AIX", "ppc64") === "osx")
  }

  // -- Defensive null/empty -----------------------------------------------

  test("null inputs do not throw; they fall back to the unknown-OS branch") {
    // The package object's call site is `os(System.getProperty("os.name"), ...)`
    // and `System.getProperty` returns null for missing properties. Making
    // this robust prevents NPEs in deployments with weird security policies.
    assert(os(null, null) === "osx")
    assert(os(null, "amd64") === "osx")
    assert(os("Linux", null) === "linux32") // arch == null → not 64-bit
  }

  test("empty inputs are treated like unknown") {
    assert(os("", "") === "osx")
    assert(os("", "amd64") === "osx")
  }
}
