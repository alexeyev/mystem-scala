package ru.stachek66.tools

import java.io.File
import java.net.URL

import org.junit.runner.RunWith
import org.scalatest.{Ignore, FunSuite}
import org.scalatest.junit.JUnitRunner

/**
 * alexeyev 
 * 07.09.14.
 */
@Ignore
class Downloader$Test extends FunSuite {

  test("downloading-something") {

    val hello = new File("hello-test.html")
    val mystem = new File("atmta.binary")

    Downloader.downloadBinaryFile(new URL("http://www.stachek66.ru/"), hello)

    Downloader.downloadBinaryFile(
      new URL("http://download.cdn.yandex.net/mystem/mystem-3.0-linux3.1-64bit.tar.gz"),
      mystem
    )

    hello.delete
    mystem.delete
  }

  test("download-and-unpack") {
    val bin = new File("atmta.binary.tar.gz")
    val bin2 = new File("executable")

    Decompressor.select.unpack(
      Downloader.downloadBinaryFile(
        new URL("http://download.cdn.yandex.net/mystem/mystem-3.0-linux3.1-64bit.tar.gz"),
        bin),
      bin2
    )

    bin.delete
    bin2.delete
  }
}
