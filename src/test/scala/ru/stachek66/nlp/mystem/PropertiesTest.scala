package ru.stachek66.nlp.mystem

import java.net.URL

import org.scalatest.funsuite.AnyFunSuite

class PropertiesTest extends AnyFunSuite {

  test("getUrl resolves the documented mystem 3.0 / 3.1 download URLs per OS") {
    assert(
      Properties.getUrl("3.0", "win32")
        === new URL("http://download.cdn.yandex.net/mystem/mystem-3.0-win7-32bit.zip")
    )
    assert(
      Properties.getUrl("3.0", "linux64")
        === new URL("http://download.cdn.yandex.net/mystem/mystem-3.0-linux3.1-64bit.tar.gz")
    )
    assert(
      Properties.getUrl("3.1", "win64")
        === new URL("http://download.cdn.yandex.net/mystem/mystem-3.1-win-64bit.zip")
    )
    assert(
      Properties.getUrl("3.1", "linux64")
        === new URL("http://download.cdn.yandex.net/mystem/mystem-3.1-linux-64bit.tar.gz")
    )
  }

  test("getUrl rejects malformed version strings") {
    val ex = intercept[IllegalArgumentException] {
      Properties.getUrl("not-a-version", "linux64")
    }
    assert(ex.getMessage.contains("pattern"))
  }

  test("getUrl reports a useful error for an unknown OS suffix") {
    val ex = intercept[Exception] {
      Properties.getUrl("3.0", "no-such-os")
    }
    assert(ex.getMessage.contains("no-such-os"))
  }
}
