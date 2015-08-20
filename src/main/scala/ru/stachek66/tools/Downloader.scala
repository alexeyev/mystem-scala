package ru.stachek66.tools

import java.io.File
import java.net.URL

import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

/**
 * alexeyev 
 * 31.08.14.
 */
object Downloader {

  private val log = LoggerFactory.getLogger(getClass)

  def downloadBinaryFile(url: URL, destination: File) = {
    log.debug(s"Getting binaries from $url, writing to $destination ")
    if (!destination.getAbsoluteFile.getParentFile.mkdirs && !destination.getAbsoluteFile.getParentFile.exists)
      throw new Exception("Could not create directory: " + destination.getParentFile)
    FileUtils.copyURLToFile(url, destination)
    log.debug("Downloading binaries done.")
    destination
  }
}
