package ru.stachek66.nlp.mystem.holding

import java.io.{File, IOException}
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

import org.slf4j.LoggerFactory
import ru.stachek66.tools.external.FailSafeExternalProcessServer
import ru.stachek66.tools.{Decompressor, Downloader, Tools}

import scala.concurrent.duration._
import scala.sys.process._
import scala.util.Try

/**
 * Provides fresh `mystem` binaries and constructs configured [[MyStem]]
 * analyzers. Roughly: "give me a MyStem 3.0; download the binary if I don't
 * already have one cached".
 */
class Factory(parsingOptions: String = "-igd --eng-gr --format json --weight") {

  import ru.stachek66.nlp.mystem.Properties._

  private val log = LoggerFactory.getLogger(getClass)

  /**
   * Construct a new [[MyStem]] analyzer.
   *
   * If `customExecutable` is `None`, the binary is fetched into
   * `~/.local/bin/` (see `Properties.BinDestination`) the first time it is
   * needed and reused thereafter.
   */
  def newMyStem(version: String, customExecutable: Option[File] = None): Try[MyStem] = Try {

    val ex: File = customExecutable.getOrElse(getExecutable(version))

    val cmd = ex.getAbsolutePath + (if (parsingOptions.nonEmpty) " " + parsingOptions else "")

    version match {
      case "3.0" | "3.1" => new MyStem3(new FailSafeExternalProcessServer(cmd))
      case other         => throw new NotImplementedError(s"mystem $other is not supported")
    }
  }

  @throws(classOf[Exception])
  private[holding] def getExecutable(version: String): File = {

    val destFile = new File(BinDestination + BIN_FILE_NAME)
    val tempFile = new File(
      s"${BinDestination}tmp_${System.currentTimeMillis}.${Decompressor.select.traditionalExtension}"
    )

    if (destFile.exists) {
      log.info("Old executable file found")

      try {
        val suggestedVersion = (destFile.getAbsolutePath + " -v").!!
        log.info("Version | " + suggestedVersion)
        if (suggestedVersion.contains(version)) destFile
        else throw new Exception("Wrong version!")
      } catch {
        case e: Exception =>
          log.warn("Removing old binary files...", e)
          val _ = destFile.delete()
          getExecutable(version)
      }
    } else {
      Tools.withAttempt(10, 1.second) {
        try {
          val unpacked =
            Decompressor.select.unpack(Downloader.downloadBinaryFile(getUrl(version), tempFile), destFile)

          // Best-effort POSIX permissions; on Windows this throws and we
          // simply skip — the file is already executable on NTFS.
          try {
            Files.setPosixFilePermissions(destFile.toPath, PosixFilePermissions.fromString("r-xr-xr-x"))
          } catch {
            case _: IOException | _: UnsupportedOperationException =>
              log.warn("Can't set POSIX permissions to file " + destFile.toPath)
          }

          unpacked
        } finally {
          val _ = tempFile.delete()
        }
      }
    }
  }
}
