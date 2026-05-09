package ru.stachek66.tools.external

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import ru.stachek66.tools.Tools

import scala.util.{Failure, Try}

/** Restart-on-failure wrapper around [[ExternalProcessServer]].
  *
  * In addition to retries, this class registers a JVM shutdown hook that
  * destroys the wrapped process when the JVM exits. Without that hook the
  * `mystem` child process would survive its parent JVM (it is not a daemon),
  * which is what causes builds to hang under Surefire on Windows — see
  * https://github.com/alexeyev/mystem-scala/issues/3 . Calling [[close]]
  * explicitly removes the hook so we don't leak it across many short-lived
  * instances inside a long-running JVM.
  */
class FailSafeExternalProcessServer(starterCommand: String, attempts: Int = 30) extends SyncServer {

  private val ps = new AtomicReference[ExternalProcessServer](new ExternalProcessServer(starterCommand))
  private val closed = new AtomicBoolean(false)

  // Held in a ref so we can deregister it from close().
  private val shutdownHook: Thread = {
    val t =
      new Thread(
        new Runnable {
          override def run(): Unit = closeSilently()
        },
        "mystem-scala-shutdown-hook"
      )
    Runtime.getRuntime.addShutdownHook(t)
    t
  }

  override def syncRequest(request: String): Try[String] = this.synchronized {
    if (closed.get()) {
      // Channel the closed-state failure through Try.Failure so MyStem3.analyze's
      // pattern-match converts it to MyStemApplicationException, like every
      // other failure mode. We deliberately return Failure rather than letting
      // Tools.withAttempt see the IllegalStateException — the retry loop would
      // otherwise bounce on it `attempts` times and wrap it in
      // "No attempts left", losing the original cause.
      Failure(new IllegalStateException("FailSafeExternalProcessServer has already been closed"))
    } else {
      Tools.withAttempt(attempts) {
        val server = ps.get()
        if (server == null || !server.isAlive) {
          // Tear down the dead server before replacing it.
          if (server != null)
            Try(server.close())
          ps.set(new ExternalProcessServer(starterCommand))
        }
        ps.get().syncRequest(request)
      }
    }
  }

  override def close(): Unit =
    if (closed.compareAndSet(false, true)) {
      // Deregister the hook on explicit close so we don't pile them up.
      // IllegalStateException happens if the JVM is already shutting down,
      // in which case the hook is firing anyway and we just continue.
      try Runtime.getRuntime.removeShutdownHook(shutdownHook)
      catch { case _: IllegalStateException => () }
      closeSilently()
    }

  private def closeSilently(): Unit = {
    val server = ps.getAndSet(null)
    if (server != null) {
      try server.close()
      catch { case _: Throwable => () }
    }
  }

}
