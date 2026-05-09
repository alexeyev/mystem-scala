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

/** Provides fresh `mystem` binaries and constructs configured [[MyStem]]
  * analyzers. Roughly: "give me a MyStem 3.0; download the binary if I don't
  * already have one cached".
  */
class Factory(parsingOptions: String = "-igd --eng-gr --format json --weight") {

  import ru.stachek66.nlp.mystem.Properties._

  private val log = LoggerFactory.getLogger(getClass)

  /** Construct a new [[MyStem]] analyzer.
    *
    * If `customExecutable` is `None`, the binary is fetched into
    * `~/.local/bin/` (see `Properties.BinDestination`) the first time it is
    * needed and reused thereafter.
    */
  def newMyStem(version: String, customExecutable: Option[File] = None): Try[MyStem] = Try {

    val ex: File = customExecutable.getOrElse(getExecutable(version))

    val cmd =
      ex.getAbsolutePath + (if (parsingOptions.nonEmpty)
                              " " + parsingOptions
                            else
                              "")

    version match {
      case "3.0" | "3.1" => new MyStem3(new FailSafeExternalProcessServer(cmd))
      case other => throw new NotImplementedError(s"mystem $other is not supported")
    }
  }

  @throws(classOf[Exception])
  private[holding] def getExecutable(version: String): File = {

    val destFile = new File(BinDestination + BIN_FILE_NAME)
    val tempFile =
      new File(
        s"${BinDestination}tmp_${System.currentTimeMillis}.${Decompressor.select.traditionalExtension}"
      )

    if (destFile.exists) {
      log.info("Old executable file found")
      if (isCorrectVersion(destFile, version)) {
        destFile
      } else {
        log.warn(s"Wrong version at ${destFile.getAbsolutePath}; removing old binary file")
        val _ = destFile.delete()
        getExecutable(version)
      }
    } else {
      Tools.withAttempt(10, 1.second) {
        try {
          val unpacked = Decompressor.select.unpack(Downloader.downloadBinaryFile(getUrl(version), tempFile), destFile)

          // Best-effort POSIX permissions; on Windows this throws and we
          // simply skip — the file is already executable on NTFS.
          try Files.setPosixFilePermissions(destFile.toPath, PosixFilePermissions.fromString("r-xr-xr-x"))
          catch {
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

  /** Run `<executable> -v` and check whether the printed version line
    * contains the requested version string. Used by [[getExecutable]] to
    * decide whether to reuse a cached binary or delete it and re-fetch.
    *
    * Returns `false` rather than throwing on any failure (file missing,
    * non-executable, exits non-zero, prints something we can't read) —
    * the caller's response to "version doesn't match" is the same as
    * "couldn't tell": delete and re-fetch.
    *
    * `private[holding]` to expose for unit testing without making it
    * part of the public Factory API.
    */
  private[holding] def isCorrectVersion(executable: java.io.File, version: String): Boolean =
    scala.util.Try {
      // `!!` runs the process and returns stdout, throwing if the command
      // can't be exec'd or exits non-zero. Both failure modes correctly
      // collapse into "Failure" here and become `false` below.
      val printed = (executable.getAbsolutePath + " -v").!!
      log.info(s"Version probe on ${executable.getName} printed: ${printed.trim}")
      printed.contains(version)
    } match {
      case scala.util.Success(matches) => matches
      case scala.util.Failure(t) =>
        log.warn(s"Could not check version of ${executable.getAbsolutePath}: ${t.getMessage}")
        false
    }

}
