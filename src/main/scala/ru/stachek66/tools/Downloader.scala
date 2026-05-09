package ru.stachek66.tools

import java.io.File
import java.net.URL

import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

object Downloader {

  private val log = LoggerFactory.getLogger(getClass)

  def downloadBinaryFile(url: URL, destination: File): File = {
    log.debug(s"Getting binaries from $url, writing to $destination ")

    val parent = destination.getAbsoluteFile.getParentFile
    if (!parent.mkdirs() && !parent.exists()) {
      throw new Exception("Could not create directory: " + parent)
    }

    FileUtils.copyURLToFile(url, destination)
    log.debug("Downloading binaries done.")
    destination
  }
}
