package ru.stachek66.tools

import java.io.{IOException, File, FileOutputStream}

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.io.IOUtils
import ru.stachek66.nlp.mystem.Properties

/**
 * alexeyev 
 * 11.09.14.
 */
trait Decompressor {

  def traditionalExtension: String

  def unpack(src: File, dst: File): File

  @throws(classOf[IOException])
  private[tools] def copyUncompressedAndClose(stream: ArchiveInputStream[_ <: ArchiveEntry], dest: File): File = {

    // must be read
    val entry = stream.getNextEntry
    if (entry.isDirectory)
      throw new IOException("Decompressed entry is a directory (unexpectedly)")

    val os = new FileOutputStream(dest)

    try {
      IOUtils.copy(stream, os)
    } finally {
      os.close()
      stream.close()
    }
    dest
  }
}

object Decompressor {
  def select: Decompressor =
    if (Properties.CurrentOs.contains("win")) Zip else TarGz
}