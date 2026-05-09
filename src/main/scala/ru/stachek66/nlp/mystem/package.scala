package ru.stachek66.nlp

import org.slf4j.LoggerFactory

package object mystem {

  private val log = LoggerFactory.getLogger(getClass)

  /**
   * Map `(os.name, os.arch)` to the suffix used in `mystem-sources.conf`.
   *
   * The previous implementation only recognized literally-`"Windows7"` and
   * fell back to macOS for everything else, including Windows 10 / 11
   * (which `System.getProperty("os.name")` reports with a space). This
   * version normalizes the OS name first and matches against a prefix.
   */
  def os(rawName: String, rawArch: String): String = {
    val name = Option(rawName).getOrElse("").toLowerCase
    val arch = Option(rawArch).getOrElse("").toLowerCase
    val is64 = arch.contains("64") || arch == "amd64" || arch == "aarch64"

    if (name.startsWith("windows")) {
      if (is64) "win64" else "win32"
    } else if (name.startsWith("linux")) {
      if (is64) "linux64" else "linux32"
    } else if (name.startsWith("freebsd")) {
      "freebsd64"
    } else if (name.startsWith("mac") || name.contains("darwin") || name.contains("os x")) {
      "osx"
    } else {
      log.warn(s"Unknown OS '$rawName' / arch '$rawArch'; falling back to macOS binaries.")
      "osx"
    }
  }
}
