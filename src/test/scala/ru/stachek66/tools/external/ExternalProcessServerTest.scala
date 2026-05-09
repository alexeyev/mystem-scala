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

  // -- syncRequest, in-process via a line-buffered awk echo ---------------

  /** Write a line-buffered echo program in Python and return its path.
    *
    * Why Python: GNU `stdbuf -oL cat` works on Linux but not on macOS (BSD
    * coreutils don't ship `stdbuf`); plain `awk '{print; fflush()}'` line-
    * buffers OUTPUT but block-buffers INPUT, so a single short line never
    * gets read until the buffer fills (~4 KB). Python's stdin iterator
    * with `sys.stdout.flush()` after every line gives us a portable,
    * bidirectionally line-buffered echo on every CI runner shipping
    * Python 3 (which is all of them since GitHub Actions ubuntu-22.04+
    * and macOS-11+).
    */
  private def pythonEchoScript(): java.io.File = {
    val f = java.io.File.createTempFile("mystem-scala-echo-", ".py")
    f.deleteOnExit()
    val body =
      """import sys
        |for line in sys.stdin:
        |    sys.stdout.write(line)
        |    sys.stdout.flush()
        |""".stripMargin
    java.nio.file.Files.write(f.toPath, body.getBytes("UTF-8"))
    f
  }

  private def hasPython3: Boolean =
    scala.util.Try {
      val p = new ProcessBuilder("python3", "--version").redirectErrorStream(true).start()
      p.waitFor() == 0
    }.getOrElse(false)

  test("syncRequest writes a line to stdin and returns the line the process emits") {
    assume(isUnixLike && hasPython3, "needs python3 + sh")
    val cmd = s"python3 -u ${pythonEchoScript().getAbsolutePath}"
    val server = new ExternalProcessServer(cmd)
    try {
      val result = server.syncRequest("hello world")
      assert(result.isSuccess, s"expected Success, got $result")
      assert(result.get === "hello world")
    } finally server.close()
  }

  test("syncRequest can be called repeatedly on the same process") {
    // The wrapper's promise is one round-trip per call, with the same
    // child surviving across calls. Pin that — a regression where the
    // process is recreated per request would be a major perf surprise.
    assume(isUnixLike && hasPython3, "needs python3 + sh")
    val cmd = s"python3 -u ${pythonEchoScript().getAbsolutePath}"
    val server = new ExternalProcessServer(cmd)
    try {
      assert(server.syncRequest("first").get === "first")
      assert(server.syncRequest("second").get === "second")
      assert(server.syncRequest("third").get === "third")
      // Process should still be the same one we started with.
      assert(server.isAlive, "the underlying process must outlive multiple syncRequest calls")
    } finally server.close()
  }

  test("syncRequest preserves Cyrillic UTF-8 round-trip") {
    // ExternalProcessServer wraps stdin/stdout in UTF-8 explicitly. Pin
    // that so a future `Charset.defaultCharset()` regression — which
    // would silently use the platform default and mangle Cyrillic — gets
    // caught.
    assume(isUnixLike && hasPython3, "needs python3 + sh")
    val cmd = s"python3 -u ${pythonEchoScript().getAbsolutePath}"
    val server = new ExternalProcessServer(cmd)
    try {
      val text = "проверка тестового запуска"
      assert(server.syncRequest(text).get === text)
    } finally server.close()
  }

  test("syncRequest after close() fails (writer already closed)") {
    // Direct close-after-syncRequest contract. The exact exception type
    // depends on the underlying Writer's behavior — what we pin is that
    // it surfaces as a Try.Failure rather than silently returning ""
    // or hanging.
    assume(isUnixLike && hasPython3, "needs python3 + sh")
    val cmd = s"python3 -u ${pythonEchoScript().getAbsolutePath}"
    val server = new ExternalProcessServer(cmd)
    server.close()
    val result = server.syncRequest("anything")
    assert(result.isFailure, s"expected Failure after close(), got $result")
  }

  test("syncRequest returns Failure when the child exits before producing any output") {
    // Pre-fix, the wrapper spun forever in `while (!reader.ready())`
    // because BufferedReader.ready() returns false at EOF, and the loop
    // never noticed the child had died. A `mystem` segfault / OOM /
    // exec-failure would jam the wrapper indefinitely. The fix uses
    // process.isAlive as a co-condition; this test pins that contract.
    assume(isUnixLike && hasPython3, "needs python3 + sh")
    // Tiny script that exits immediately on its first input. No echo.
    val script = java.io.File.createTempFile("mystem-scala-noecho-", ".py")
    script.deleteOnExit()
    java.nio.file.Files.write(
      script.toPath,
      "import sys\nsys.stdin.readline()\nsys.exit(0)\n".getBytes("UTF-8")
    )
    val server = new ExternalProcessServer(s"python3 -u ${script.getAbsolutePath}")
    try {
      val result = server.syncRequest("hello")
      assert(result.isFailure, s"expected Failure (process exited), got $result")
      assert(
        result.failed.get.isInstanceOf[java.io.IOException],
        s"expected IOException, got ${result.failed.get.getClass.getName}"
      )
    } finally server.close()
  }
}
