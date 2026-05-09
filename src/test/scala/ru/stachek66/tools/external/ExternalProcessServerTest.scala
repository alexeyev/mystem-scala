package ru.stachek66.tools.external

import org.scalatest.funsuite.AnyFunSuite

/** Behavioral spec for [[ExternalProcessServer]].
  *
  * `ExternalProcessServer` is package-private, but tests in the same package
  * can reach it directly. We exercise the lifecycle here via the `isAlive`
  * accessor — that's the strongest in-process check we have without forking
  * another JVM (the shutdown-hook firing test for [[FailSafeExternalProcessServer]]
  * does that separately).
  *
  * `/bin/sleep` is the smallest, most portable long-running stand-in for a
  * real `mystem`. It's available on every Unix-like CI runner Ubuntu /
  * macOS, has no startup quirks, and never reads its stdin or writes to its
  * stdout — exactly what we need for a lifecycle-only check.
  */
class ExternalProcessServerTest extends AnyFunSuite {

  private val isUnixLike: Boolean = !sys.props("os.name").toLowerCase.startsWith("windows")

  /** Poll a predicate up to `timeoutMs`, returning whether it became true. */
  private def awaitUntil(timeoutMs: Long, stepMs: Long = 25)(p: => Boolean): Boolean = {
    val deadline = System.currentTimeMillis() + timeoutMs
    var ok = p
    while (!ok && System.currentTimeMillis() < deadline) {
      Thread.sleep(stepMs)
      ok = p
    }
    ok
  }

  test("constructor starts the underlying OS process and isAlive is true") {
    assume(isUnixLike, "needs /bin/sleep")
    val server = new ExternalProcessServer("/bin/sleep 60")
    try assert(server.isAlive, "process should be live immediately after construction")
    finally server.close()
  }

  test("close() destroys the underlying process: isAlive transitions to false") {
    assume(isUnixLike, "needs /bin/sleep")
    val server = new ExternalProcessServer("/bin/sleep 60")
    assert(server.isAlive)
    server.close()
    // close() in our implementation already waits up to 1s for graceful
    // exit and then forcibly destroys; we still poll briefly to absorb any
    // CI-host scheduling jitter between destroy() and the OS marking the
    // process exited.
    assert(
      awaitUntil(2000)(!server.isAlive),
      "isAlive must become false within 2 seconds of close()"
    )
  }

  test("close() returns promptly even on a process that ignores SIGTERM") {
    assume(isUnixLike, "needs /bin/sh")
    // `sh -c 'trap "" TERM; sleep 60'` would be the right shape but we
    // can't pass quoted arguments through the whitespace-split command
    // parser. Instead, spawn via a tiny inline shell-trick file: write a
    // script that traps SIGTERM and sleeps. We then verify close() still
    // returns within the documented forcibly-destroy bound (1s grace
    // period + small slack).
    val script = java.io.File.createTempFile("mystem-scala-trap-", ".sh")
    script.deleteOnExit()
    val body =
      """#!/bin/sh
        |trap '' TERM
        |sleep 60
        |""".stripMargin
    java.nio.file.Files.write(script.toPath, body.getBytes("UTF-8"))
    val _ = script.setExecutable(true)

    val server = new ExternalProcessServer(script.getAbsolutePath)
    val started = System.currentTimeMillis()
    server.close()
    val elapsed = System.currentTimeMillis() - started

    // 1s graceful grace period is in the production code; the kill-9 step
    // is essentially instant. Anything north of ~3s means we hung waiting
    // for the SIGTERM-deaf process — exactly the bug we're guarding.
    assert(elapsed < 3000, s"close() took ${elapsed}ms on a SIGTERM-deaf process; should forcibly destroy")
    assert(awaitUntil(2000)(!server.isAlive))
  }

  test("close() is idempotent across many calls") {
    assume(isUnixLike, "needs /bin/sleep")
    val server = new ExternalProcessServer("/bin/sleep 60")
    server.close()
    // Calling close many times must never throw and must keep isAlive false.
    (1 to 5).foreach(_ => server.close())
    assert(!server.isAlive)
  }
}
