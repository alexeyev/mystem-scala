package ru.stachek66.nlp.mystem

import org.scalatest.funsuite.AnyFunSuite

class OsDetectionTest extends AnyFunSuite {

  test("Windows 10 / 11 (with a space) is recognized as Windows, not macOS") {
    // The previous implementation only matched the literal "Windows7" and
    // fell back to macOS for everything else; this regression test guards
    // against re-introducing that behavior.
    assert(os("Windows 10", "amd64") === "win64")
    assert(os("Windows 11", "x86_64") === "win64")
    assert(os("Windows 7", "x86") === "win32")
  }

  test("Linux is recognized in both common arch spellings") {
    assert(os("Linux", "amd64") === "linux64")
    assert(os("Linux", "x86_64") === "linux64")
    assert(os("Linux", "aarch64") === "linux64")
    assert(os("Linux", "x86") === "linux32")
  }

  test("macOS is recognized under several os.name spellings") {
    assert(os("Mac OS X", "x86_64") === "osx")
    assert(os("Darwin", "arm64") === "osx")
  }

  test("FreeBSD is recognized") {
    assert(os("FreeBSD", "amd64") === "freebsd64")
  }

  test("An unknown OS falls back to the macOS suffix (with a warning)") {
    // We don't promise this fallback forever, but tests should pin it so
    // a behavior change is intentional.
    assert(os("Plan9", "x86_64") === "osx")
  }

  test("Null inputs do not throw") {
    assert(os(null, null) === "osx")
  }
}
