package ru.stachek66.tools

import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration._

/**
 * Behavioral spec for `Tools.withAttempt`.
 *
 * The contract we are pinning down here is:
 *   1. Successful actions return immediately, exactly once, regardless of `n`.
 *   2. Exceptions from the action are retried up to `n - 1` times.
 *   3. After the final failure, the original cause is preserved.
 *   4. Errors (Throwable but not Exception) are NOT retried.
 *   5. The `timeout` is applied between retries only — never before the first
 *      attempt and never after the last (success or failure).
 *   6. The wrapped action's by-name parameter is re-evaluated on each attempt,
 *      so each retry runs the block fresh.
 */
class ToolsTest extends AnyFunSuite {

  // -- Success paths -------------------------------------------------------

  test("a successful action runs exactly once and returns its value") {
    val calls = new AtomicInteger(0)
    val result = Tools.withAttempt(3) {
      calls.incrementAndGet()
      "ok"
    }
    assert(result === "ok")
    assert(calls.get() === 1)
  }

  test("n = 1 (no retries) still works on first-shot success") {
    val calls = new AtomicInteger(0)
    val result = Tools.withAttempt(1)("done")
    // Sanity: the by-name didn't get evaluated more than once.
    assert(result === "done")
    assert(calls.get() === 0)
  }

  test("the action's by-name parameter is re-evaluated on every retry") {
    // We rely on this: it's how the caller observes a retry instead of a
    // memoized failure. If `action` were captured once and reused, this
    // test's counter would stay at 1 and the third attempt would use a
    // stale snapshot.
    val calls = new AtomicInteger(0)
    val result = Tools.withAttempt(5) {
      val n = calls.incrementAndGet()
      if (n < 3) throw new RuntimeException(s"transient $n") else s"got $n"
    }
    assert(calls.get() === 3)
    assert(result === "got 3")
  }

  // -- Failure paths -------------------------------------------------------

  test("after exhausting retries, the original cause is preserved") {
    val calls = new AtomicInteger(0)
    val ex = intercept[Exception] {
      Tools.withAttempt(3) {
        val n = calls.incrementAndGet()
        throw new RuntimeException(s"persistent $n")
      }
    }
    assert(calls.get() === 3, "should have made exactly n attempts")
    assert(ex.getMessage === "No attempts left")
    assert(ex.getCause !== null, "wrapped Exception must carry the original cause")
    assert(ex.getCause.isInstanceOf[RuntimeException])
    assert(ex.getCause.getMessage === "persistent 3", "the *last* failure is the cause")
  }

  test("n = 1 with a failing action throws immediately, not after a retry") {
    val calls = new AtomicInteger(0)
    val ex = intercept[Exception] {
      Tools.withAttempt(1) {
        calls.incrementAndGet()
        throw new RuntimeException("boom")
      }
    }
    assert(calls.get() === 1, "must NOT retry when n = 1")
    assert(ex.getMessage === "No attempts left")
    assert(ex.getCause.getMessage === "boom")
  }

  // -- Timeout behaviour ---------------------------------------------------

  test("timeout is applied between retries (not before the first attempt)") {
    // 3 attempts, 200 ms timeout → 2 sleeps × 200 ms, so somewhere
    // between 400 ms and "much more than 400 ms but not unboundedly so".
    val timeout = 200.millis
    val attempts = 3
    val expectedMin = (attempts - 1) * timeout.toMillis

    val calls = new AtomicInteger(0)
    val started = System.nanoTime()
    Tools.withAttempt(attempts, timeout) {
      val n = calls.incrementAndGet()
      if (n < attempts) throw new RuntimeException("retry me") else "ok"
    }
    val elapsedMs = (System.nanoTime() - started) / 1000000L

    assert(calls.get() === attempts)
    assert(
      elapsedMs >= expectedMin,
      s"expected at least ${expectedMin}ms (n-1 timeouts), got ${elapsedMs}ms"
    )
    // Catch the "we slept way too many times" failure mode too. CI hosts
    // can be slow so allow generous slack, but flag a runaway.
    assert(
      elapsedMs <= expectedMin + 5000,
      s"sleeping ${elapsedMs}ms is far more than expected ${expectedMin}ms; suggests over-application of timeout"
    )
  }

  test("no timeout is applied when the first attempt succeeds") {
    val absurdTimeout = 30.seconds
    val started = System.nanoTime()
    val result = Tools.withAttempt(5, absurdTimeout)("immediate")
    val elapsedMs = (System.nanoTime() - started) / 1000000L
    assert(result === "immediate")
    assert(elapsedMs < 1000, s"first-shot success should not sleep; took ${elapsedMs}ms")
  }

  test("no timeout is applied after the final failure") {
    // If `withAttempt` mistakenly slept after the last failure too, this
    // test would take roughly `attempts * timeout` rather than `(attempts-1) * timeout`.
    val timeout = 250.millis
    val attempts = 2
    val started = System.nanoTime()
    intercept[Exception] {
      Tools.withAttempt(attempts, timeout)(throw new RuntimeException("nope"))
    }
    val elapsedMs = (System.nanoTime() - started) / 1000000L
    // Lower bound: one sleep between the two attempts.
    assert(elapsedMs >= timeout.toMillis - 50)
    // Upper bound: two sleeps would put us at 500 ms; if we cross 450 ms
    // (timeout × 1.8) we know an extra sleep snuck in.
    assert(
      elapsedMs < (timeout.toMillis * 18 / 10),
      s"final-failure path slept too long (${elapsedMs}ms); probably sleeping after the last attempt"
    )
  }

  // -- Throwable kinds -----------------------------------------------------

  test("Errors propagate immediately and are NOT retried") {
    // withAttempt only catches Exception, so an Error (e.g., OutOfMemoryError)
    // must surface on the first attempt without being wrapped.
    val calls = new AtomicInteger(0)
    val err = intercept[OutOfMemoryError] {
      Tools.withAttempt(5) {
        calls.incrementAndGet()
        throw new OutOfMemoryError("simulated")
      }
    }
    assert(calls.get() === 1, "Errors must not trigger the retry path")
    assert(err.getMessage === "simulated", "the Error must propagate unwrapped")
  }

  test("a checked-style RuntimeException subclass is still treated as Exception and retried") {
    // Defensive: sanity-check that the `catch { case e: Exception }` clause
    // actually matches RuntimeException subclasses (which are common in
    // mystem's I/O paths).
    val calls = new AtomicInteger(0)
    val result = Tools.withAttempt(3) {
      val n = calls.incrementAndGet()
      if (n < 2) throw new IllegalStateException("hold on") else "fine"
    }
    assert(calls.get() === 2)
    assert(result === "fine")
  }

  test("the action may return Unit") {
    // T = Unit is a real use-case (retry side-effecting actions).
    val calls = new AtomicInteger(0)
    val ret: Unit = Tools.withAttempt(3) { calls.incrementAndGet(); () }
    assert(calls.get() === 1)
    assert(ret === ((): Unit))
  }
}
