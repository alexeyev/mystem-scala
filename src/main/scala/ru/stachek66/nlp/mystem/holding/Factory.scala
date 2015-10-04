package ru.stachek66.nlp.mystem.holding

import java.io.{File, IOException}
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

import org.slf4j.LoggerFactory
import ru.stachek66.tools.external.FailSafeExternalProcessServer
import ru.stachek66.tools.{Decompressor, Downloader, Tools}

import scala.concurrent.duration._
import scala.sys.process._

/**
 * Provides fresh mystem binaries; a factory
 * alexeyev
 * 31.08.14.
 */
class Factory(parsingOptions: String = "-igd --eng-gr --format json --weight") {

  import ru.stachek66.nlp.mystem.Properties._

  private val log = LoggerFactory.getLogger(getClass)

  /**
   * Creates a new instance of mystem server
   * Uses .local if customExecutable was not set
   */
  def newMyStem(version: String, customExecutable: Option[File] = None): MyStem = {

    val ex = customExecutable match {
      case Some(exe) => exe
      case None => getExecutable(version)
    }

    version match {
      case "3.0" =>
        new MyStem30(
          new FailSafeExternalProcessServer(
            ex.getAbsolutePath + (if (parsingOptions.nonEmpty) " " + parsingOptions else "")))
      case _ => throw new NotImplementedError()
    }
  }

  private[holding] def getExecutable(version: String): File = {

    val destFile = new File(BinDestination + BIN_FILE_NAME)
    val tempFile = new File(s"${BinDestination}tmp_${System.currentTimeMillis}.${Decompressor.select.traditionalExtension}")

    if (destFile.exists) {

      log.info("Old executable file found")

      try {
        val suggestedVersion = (destFile.getAbsolutePath + " -v") !!

        log.info("version | " + suggestedVersion)
        // not scala-way stuff
        if (suggestedVersion.contains(version)) destFile
        else throw new Exception("wrong version!")
      } catch {
        case e: Exception =>
          log.warn("Removing old binary files...", e)
          destFile.delete
          getExecutable(version)
      }
    } else Tools.withAttempt(10, 1.second) {
      try {
        Decompressor.select.unpack(
          Downloader.downloadBinaryFile(getUrl(version), tempFile),
          destFile)
      } finally {
        tempFile.delete()
        try {
          Files.setPosixFilePermissions(destFile.toPath, PosixFilePermissions.fromString("r-xr-xr-x")).toFile
        } catch {
          case ioe: IOException =>
            log.warn("Can't set POSIX permissions to file " + destFile.toPath)
            destFile
        }
      }
    }
  }
}