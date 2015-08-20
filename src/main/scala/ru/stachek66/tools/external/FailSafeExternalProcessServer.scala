package ru.stachek66.tools.external

import java.util.concurrent.atomic.AtomicReference

import ru.stachek66.tools.Tools

import scala.util.Try

/**
 * alexeyev 
 * 16.10.14.
 */
class FailSafeExternalProcessServer(starterCommand: String, attempts: Int = 30) extends SyncServer {

  private val ps = new AtomicReference[ExternalProcessServer](new ExternalProcessServer(starterCommand))

  override def syncRequest(request: String): Try[String] = this.synchronized {
    Tools.withAttempt(attempts) {
      if (!ps.get.isAlive) ps.set(new ExternalProcessServer(starterCommand))
      ps.get.syncRequest(request)
    }
  }
}