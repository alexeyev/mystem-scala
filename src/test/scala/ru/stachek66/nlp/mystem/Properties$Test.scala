package ru.stachek66.nlp.mystem

import java.net.URL

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

/**
 * alexeyev 
 * 31.08.14.
 */
class Properties$Test extends FunSuite {

  test("getting-download-url") {

    assert(Properties.getUrl("3.0", "win32") === new URL("http://download.cdn.yandex.net/mystem/mystem-3.0-win7-32bit.zip"))
    assert(Properties.getUrl("3.0", "linux64") === new URL("http://download.cdn.yandex.net/mystem/mystem-3.0-linux3.1-64bit.tar.gz"))
    assert(Properties.getUrl("3.1", "win64") === new URL("http://download.cdn.yandex.net/mystem/mystem-3.1-win-64bit.zip"))
    assert(Properties.getUrl("3.1", "linux64") === new URL("http://download.cdn.yandex.net/mystem/mystem-3.1-linux-64bit.tar.gz"))

  }
}