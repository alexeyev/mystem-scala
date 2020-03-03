package ru.stachek66.nlp

import org.slf4j.LoggerFactory

/**
  * alexeyev
  * 11.09.14.
  */
package object mystem {

  private val log = LoggerFactory.getLogger(getClass)

  val os: Map[(String, String), String] = Map(
    ("Linux", "x86_64") -> "linux64",
    ("Linux", "amd64") -> "linux64",
    ("Linux", "x86") -> "linux32",
    ("Windows7", "x86") -> "win32",
    ("Windows7", "x86_64") -> "win64"
  ) withDefault {
    _ =>
      log.warn("Getting OSX binaries!")
      "osx"
  }
}