package ru.stachek66.nlp.mystem

import java.net.URL

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
 * Various configs for interaction with outer world
 * alexeyev 
 * 31.08.14.
 */
object Properties {

  private val log = LoggerFactory.getLogger(getClass)

  val BinDestination = System.getProperty("user.home") + "/.local/bin/"

  private val systemOsName = System.getProperty("os.name")
  private val systemOsArchitecture = System.getProperty("os.arch")
  val CurrentOs = os(systemOsName, systemOsArchitecture)

  log.debug(s"OS detected: $CurrentOs, system properties: $systemOsName | $systemOsArchitecture ")

  val BIN_FILE_NAME = CurrentOs match {
    case name if name.startsWith("win") => "mystem.exe"
    case name => "mystem"
//    case _ => throw new Exception("Couldn't determine the OS")
  }

  private lazy val rootProp = ConfigFactory.load("mystem-sources.conf")
  private lazy val version = rootProp.getConfig("version")

  private val versionPattern = "\\d+\\.\\d+".r.pattern

  private def doOrDie[T](action: => T, message: String = "Unknown error"): T =
    try action
    catch {
      case e: Throwable => throw new Exception(message)
    }

  def getUrl(versionRaw: String, os: String = CurrentOs): URL = {

    require(versionPattern.matcher(versionRaw).matches,
      "Troubles with version name, should match pattern <number>.<number>")

    val versionProps =
      doOrDie(
        version.getConfig(versionRaw),
        s"No binaries sources for version [$versionRaw] found")

    val url =
      doOrDie(
        versionProps.getString(os),
        s"Version number is correct, no binaries sources for OS [$os] found")

    doOrDie(
      new URL(url),
      s"URL configs troubles. If you see this message, please email anton.m.alexeyev@gmail.com")
  }
}
