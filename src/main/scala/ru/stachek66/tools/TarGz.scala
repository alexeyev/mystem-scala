package ru.stachek66.tools

import java.io.{IOException, File, FileInputStream}
import java.util.zip.GZIPInputStream

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * *.tar.gz files decompression tool
 * alexeyev 
 * 31.08.14.
 */
private object TarGz extends Decompressor {

  private val log = LoggerFactory.getLogger(getClass)

  def traditionalExtension: String = "tar.gz"

  /**
   * Untars -single- file
   */
  @throws(classOf[IOException])
  def unpack(src: File, dst: File): File = {

    log.debug(s"Unpacking $src to $dst...")

    val tarIn =
      new TarArchiveInputStream(
        new GZIPInputStream(
          new FileInputStream(src)))

    val result = copyUncompressedAndClose(tarIn, dst)
    log.debug(s"Done.")
    result
  }
}
