package ru.stachek66.tools

import java.io.{IOException, BufferedInputStream, File, FileInputStream}

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.slf4j.LoggerFactory

/**
 * *.zip decompressor tool
 * alexeyev 
 * 31.08.14.
 */
private object Zip extends Decompressor {

  private val log = LoggerFactory.getLogger(getClass)

  def traditionalExtension: String = "zip"

  /**
   * Unzips single file
   */
  @throws(classOf[IOException])
  def unpack(src: File, dst: File): File = {

    log.debug(s"Unpacking $src to $dst...")

    val zipIn = new ZipArchiveInputStream(
      new BufferedInputStream(
        new FileInputStream(src)))

    val res = copyUncompressedAndClose(zipIn, dst)

    log.debug("Done.")

    res
  }
}