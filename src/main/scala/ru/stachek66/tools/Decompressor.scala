package ru.stachek66.tools

import java.io.{File, FileOutputStream}

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.io.IOUtils
import ru.stachek66.nlp.mystem.Properties

/**
 * alexeyev 
 * 11.09.14.
 */
trait Decompressor {

  def traditionalExtension: String

  def unpack(src: File, dst: File): File

  private[tools] def copyUncompressedAndClose(stream: ArchiveInputStream, dest: File): File = {
    // must be read
    val entry = stream.getNextEntry
    if (entry.isDirectory) throw new Exception("Crappy binaries are being used")

    val os = new FileOutputStream(dest)
    try IOUtils.copy(stream, os)
    finally {
      os.close()
      stream.close()
    }
    dest
  }
}

object Decompressor {
  //todo: use OS enums
  def select: Decompressor =
    if (Properties.CurrentOs.contains("win")) Zip else TarGz
}
