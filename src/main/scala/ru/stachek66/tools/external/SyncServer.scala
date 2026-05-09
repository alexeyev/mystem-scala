package ru.stachek66.tools.external

import scala.util.Try

/**
 * Sync request/response contract over an external process.
 *
 * Implementations own an OS process and therefore extend [[AutoCloseable]];
 * `close()` must be safe to call from a JVM shutdown hook.
 */
trait SyncServer extends AutoCloseable {

  /** Send `request`; return the response or a wrapped failure. */
  def syncRequest(request: String): Try[String]

  /** Tear the underlying process down. Idempotent. */
  override def close(): Unit
}
