package ru.stachek66.tools.external

import scala.util.Try

/**
 * alexeyev 
 * 16.10.14.
 */
trait SyncServer {

  /**
   * You give it a string, and you get either response string or nothing.
   */
  def syncRequest(request: String): Try[String]

}
