package ru.stachek66.tools

import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object Tools {

  private val log = LoggerFactory.getLogger(getClass)

  /**
   * Run `action`; on Exception, retry up to `n - 1` more times with `timeout`
   * between attempts. The final failure is wrapped so the caller sees the
   * cause in the stack trace rather than just "No attempts left".
   */
  @throws(classOf[Exception])
  def withAttempt[T](n: Int, timeout: Duration = 0.millis)(action: => T): T = try action
  catch {
    case e: Exception if n > 1 =>
      log.warn(s"${n - 1} attempts left", e)
      Thread.sleep(timeout.toMillis)
      withAttempt(n - 1, timeout)(action)
    case e: Exception =>
      throw new Exception("No attempts left", e)
  }
}
