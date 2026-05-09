package ru.stachek66.tools

import java.io.{File, FileOutputStream, IOException}

import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream}
import org.apache.commons.io.IOUtils
import ru.stachek66.nlp.mystem.Properties

trait Decompressor {

  def traditionalExtension: String

  def unpack(src: File, dst: File): File

  /**
   * Copy the first entry of `stream` to `dest`, closing both streams.
   * The mystem release archives only contain a single executable, so we
   * deliberately ignore everything past the first entry.
   */
  @throws(classOf[IOException])
  private[tools] def copyUncompressedAndClose(
    stream: ArchiveInputStream[_ <: ArchiveEntry],
    dest: File
  ): File = {
    try {
      val entry = stream.getNextEntry
      if (entry == null) throw new IOException("Archive is empty")
      if (entry.isDirectory) throw new IOException("Decompressed entry is a directory (unexpectedly)")

      val os = new FileOutputStream(dest)
      try IOUtils.copy(stream, os)
      finally os.close()
    } finally stream.close()
    dest
  }
}

object Decompressor {
  def select: Decompressor =
    if (Properties.CurrentOs.contains("win")) Zip else TarGz
}
