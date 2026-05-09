package ru.stachek66.tools.external

import java.io.{BufferedReader, File, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite

/**
 * Behavioral spec for [[FailSafeExternalProcessServer]].
 *
 * The wrapper is responsible for three things on top of a plain
 * [[ExternalProcessServer]]:
 *   - retry-on-failure (covered by [[ru.stachek66.tools.ToolsTest]]);
 *   - JVM-shutdown-hook-driven cleanup of the spawned OS process —
 *     this is the fix for issue #3, exercised end-to-end below by forking
 *     a child JVM and observing that the spawned `sleep` process really is
 *     killed when the child exits without calling `close()`;
 *   - a state machine that refuses further `syncRequest` calls after
 *     `close()` (preventing a hookless replacement from being silently
 *     spawned, which would re-introduce the original leak).
 */
class FailSafeExternalProcessServerTest extends AnyFunSuite {

  private val isUnixLike: Boolean = !sys.props("os.name").toLowerCase.startsWith("windows")
  private val procFsAvailable: Boolean = isUnixLike && new File("/proc").isDirectory

  // -- Idempotence and state machine --------------------------------------

  test("close() is idempotent") {
    assume(isUnixLike, "needs /bin/sleep")
    val server = new FailSafeExternalProcessServer("/bin/sleep 60")
    server.close()
    server.close()
    server.close()
  }

  test("syncRequest after close() returns Failure(IllegalStateException), NOT a fresh hookless server") {
    // The original implementation would silently spawn a replacement when
    // ps == null after close(). That replacement would have no shutdown
    // hook, re-introducing the very leak the wrapper exists to prevent.
    // The contract we pin: after close(), syncRequest fails — and the
    // failure travels through Try.Failure (not a thrown exception), so
    // callers like MyStem3.analyze can wrap it consistently.
    assume(isUnixLike, "needs /bin/sleep")
    val server = new FailSafeExternalProcessServer("/bin/sleep 60")
    server.close()
    val result = server.syncRequest("anything")
    assert(result.isFailure, "must be a Failure, not Success")
    assert(
      result.failed.get.isInstanceOf[IllegalStateException],
      s"expected IllegalStateException, got ${result.failed.get.getClass.getName}"
    )
  }

  test("two FailSafe instances can be closed independently") {
    // Bookkeeping check: both register their own shutdown hook, and close()
    // on one must not affect the other.
    assume(isUnixLike, "needs /bin/sleep")
    val a = new FailSafeExternalProcessServer("/bin/sleep 60")
    val b = new FailSafeExternalProcessServer("/bin/sleep 60")
    try {
      a.close()
      // b is still operational. We can't trivially exercise a syncRequest
      // round-trip without an echo binary, but we can verify that close()
      // on b runs without complaint.
      b.close()
    } finally {
      a.close()
      b.close()
    }
  }

  // -- The actual #3 fix: shutdown hook fires on JVM exit -----------------

  test("JVM shutdown hook destroys the spawned process even without explicit close()") {
    assume(procFsAvailable, "needs /proc/<pid>/ to verify process death; Linux only")

    // 1. Write a tiny script that records its PID and then becomes `sleep`
    //    via `exec`. The exec preserves the PID, so the file we write
    //    ends up containing the live `sleep` PID.
    val script = File.createTempFile("mystem-scala-pid-", ".sh")
    script.deleteOnExit()
    val scriptBody =
      """#!/bin/sh
        |echo $$ > "$1"
        |exec sleep 60
        |""".stripMargin
    Files.write(script.toPath, scriptBody.getBytes(StandardCharsets.UTF_8))
    val _ = script.setExecutable(true)

    val pidFile = File.createTempFile("mystem-scala-pid-", ".txt")
    val _2 = pidFile.delete() // we want it absent so the child can write fresh
    pidFile.deleteOnExit()

    // 2. Fork a child JVM that constructs a FailSafeExternalProcessServer
    //    pointing at the script, prints the spawned PID, and exits — *without*
    //    calling close(). If the shutdown hook works, the spawned `sleep`
    //    process dies along with the child JVM. If it doesn't, `sleep`
    //    outlives the child, becomes orphaned to init, and continues for
    //    the full 60s.
    val javaBin = System.getProperty("java.home") + "/bin/java"
    val classpath = System.getProperty("java.class.path")

    val pb = new ProcessBuilder(
      javaBin,
      "-cp", classpath,
      "ru.stachek66.tools.external.ShutdownHookFiringChild",
      script.getAbsolutePath,
      pidFile.getAbsolutePath
    )
    pb.redirectErrorStream(true)
    val child = pb.start()

    // 3. Read the spawned-process PID from child stdout.
    val reader = new BufferedReader(new InputStreamReader(child.getInputStream, StandardCharsets.UTF_8))
    val output = new StringBuilder
    var line: String = reader.readLine()
    while (line != null) {
      output.append(line).append('\n')
      line = reader.readLine()
    }
    val exit = child.waitFor()
    assert(exit === 0, s"child JVM exited with $exit; stdout was:\n${output}")

    val pid = output
      .toString()
      .split('\n')
      .find(_.startsWith("PID="))
      .map(_.stripPrefix("PID=").trim)
      .getOrElse(fail(s"child JVM did not print a PID line. stdout was:\n${output}"))

    // 4. Poll /proc/<pid> for up to 5s. If the shutdown hook did its job,
    //    the entry should disappear shortly after the child JVM exited.
    val procEntry = new File(s"/proc/$pid")
    val deadline = System.currentTimeMillis() + 5000
    while (procEntry.exists() && System.currentTimeMillis() < deadline) {
      Thread.sleep(25L)
    }
    if (procEntry.exists()) {
      // Best-effort cleanup so a buggy build doesn't leave 60s sleep zombies
      // around indefinitely. Any failure here is informational.
      val _ = Runtime.getRuntime.exec(Array("kill", "-9", pid)).waitFor()
      fail(s"PID $pid was still alive 5s after the child JVM exited; the shutdown hook did NOT fire (or did not destroy the spawned process). This is a regression of issue #3.")
    }
  }
}
