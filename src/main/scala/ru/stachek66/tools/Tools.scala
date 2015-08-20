package ru.stachek66.tools

import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
 * alexeyev 
 * 02.09.14.
 */
object Tools {

  private val log = LoggerFactory.getLogger(getClass)

  def withAttempt[T](n: Int, timeout: Duration = 0.millis)(action: => T): T = try {
    action
  } catch {
    case e: Exception if n > 1 =>
      log.warn(s"${n - 1} attempts left", e)
      Thread.sleep(timeout.toMillis)
      withAttempt(n - 1)(action)
    case e: Exception =>
      throw new Exception("No attempts left", e)
  }
}