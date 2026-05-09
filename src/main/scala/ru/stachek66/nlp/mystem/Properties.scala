package ru.stachek66.nlp.mystem

import java.net.{URI, URL}

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/** Various configs for interaction with the outer world.
  */
object Properties {

  private val log = LoggerFactory.getLogger(getClass)

  val BinDestination: String = System.getProperty("user.home") + "/.local/bin/"

  private val systemOsName = System.getProperty("os.name")
  private val systemOsArchitecture = System.getProperty("os.arch")
  val CurrentOs: String = os(systemOsName, systemOsArchitecture)

  log.debug(s"OS detected: $CurrentOs, system properties: $systemOsName | $systemOsArchitecture ")

  val BIN_FILE_NAME: String =
    CurrentOs match {
      case name if name.startsWith("win") => "mystem.exe"
      case _ => "mystem"
    }

  private lazy val rootProp = ConfigFactory.load("mystem-sources.conf")
  private lazy val version = rootProp.getConfig("version")

  private val versionPattern = "\\d+\\.\\d+".r.pattern

  /** Run `action`; if it throws, rethrow as an Exception preserving the cause. */
  private def doOrDie[T](action: => T, message: String): T =
    try action
    catch {
      case t: Throwable => throw new Exception(message, t)
    }

  @throws(classOf[Exception])
  def getUrl(versionRaw: String, os: String = CurrentOs): URL = {

    require(
      versionPattern.matcher(versionRaw).matches,
      "Troubles with version name, should match pattern <number>.<number>"
    )

    val versionProps = doOrDie(version.getConfig(versionRaw), s"No binaries sources for version [$versionRaw] found")

    val url = doOrDie(versionProps.getString(os), s"Version number is correct, no binaries sources for OS [$os] found")

    doOrDie(
      // `new URL(String)` was deprecated in Java 20+. URI.toURL has been
      // there since Java 1.4 and behaves the same way for our purposes.
      URI.create(url).toURL(),
      s"URL configs troubles. If you see this message, please email anton.m.alexeyev@gmail.com"
    )
  }

}
