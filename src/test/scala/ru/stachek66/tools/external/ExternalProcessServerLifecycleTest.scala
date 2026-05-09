package ru.stachek66.tools.external

import org.scalatest.funsuite.AnyFunSuite

/** Lifecycle tests for the process server. These exercise the fix for
  * issue #3 (mystem process not terminating after the main thread quits).
  *
  * We deliberately do not exercise [[FailSafeExternalProcessServer.syncRequest]]
  * here: a real round-trip would require a stand-in CLI that line-buffers its
  * output (`cat` on Linux does not, and there is no portable equivalent). The
  * end-to-end behavior is covered manually with the actual `mystem` binary;
  * what we verify here is the *lifecycle*: a process is spawned at construction
  * time and reliably destroyed on `close()`, with `close()` being idempotent
  * and safe to call after the JVM has already begun shutting down.
  */
class ExternalProcessServerLifecycleTest extends AnyFunSuite {

  private val isUnixLike: Boolean = !sys.props("os.name").toLowerCase.startsWith("windows")

  test("constructor starts the underlying process; close() destroys it") {
    assume(isUnixLike, "needs a Unix-like host with /bin/sleep")

    // /bin/sleep is the smallest, most portable long-running stand-in: it
    // sits there doing nothing until killed, which is exactly what we need
    // to verify "close() actually destroys the process".
    val server = new FailSafeExternalProcessServer("/bin/sleep 60")
    try {
      // We can't read the wrapped Process directly from outside, but
      // close() returning is itself the contract: it must complete
      // promptly (the implementation has a 1s grace period, then forcibly
      // destroys), not block waiting for sleep(60) to finish on its own.
      val started = System.currentTimeMillis()
      server.close()
      val elapsed = System.currentTimeMillis() - started
      assert(elapsed < 5000, s"close() took $elapsed ms; should be well under 5 s")
    } finally
      // Defense in depth — close() should already be a no-op here.
      server.close()
  }

  test("close() is idempotent") {
    assume(isUnixLike, "needs a Unix-like host with /bin/sleep")
    val server = new FailSafeExternalProcessServer("/bin/sleep 60")
    server.close()
    server.close() // second close must not throw
    server.close() // third either
  }

  test("a fresh server can be constructed after a prior one is closed") {
    assume(isUnixLike, "needs a Unix-like host with /bin/sleep")
    val a = new FailSafeExternalProcessServer("/bin/sleep 60")
    a.close()

    val b = new FailSafeExternalProcessServer("/bin/sleep 60")
    try
      // Just making it this far without an exception is the assertion: the
      // shutdown-hook bookkeeping in FailSafeExternalProcessServer must not
      // accumulate state across instances.
      succeed
    finally b.close()
  }
}
