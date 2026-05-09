package ru.stachek66.tools

import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration._

class ToolsTest extends AnyFunSuite {

  test("withAttempt returns immediately on a successful action") {
    val calls = new AtomicInteger(0)
    val result =
      Tools.withAttempt(3) {
        calls.incrementAndGet()
        "ok"
      }
    assert(result === "ok")
    assert(calls.get() === 1)
  }

  test("withAttempt retries up to n-1 more times on Exception") {
    val calls = new AtomicInteger(0)
    val result =
      Tools.withAttempt(4) {
        val attempt = calls.incrementAndGet()
        if (attempt < 3)
          throw new RuntimeException(s"transient $attempt")
        else
          "ok"
      }
    assert(result === "ok")
    assert(calls.get() === 3)
  }

  test("withAttempt rethrows after the last attempt with the original cause attached") {
    val calls = new AtomicInteger(0)
    val ex = intercept[Exception] {
      Tools.withAttempt(2) {
        val n = calls.incrementAndGet()
        throw new RuntimeException(s"persistent $n")
      }
    }
    assert(calls.get() === 2)
    assert(ex.getMessage === "No attempts left")
    assert(ex.getCause !== null)
    assert(ex.getCause.getMessage === "persistent 2")
  }

  test("withAttempt sleeps `timeout` between retries") {
    val calls = new AtomicInteger(0)
    val start = System.currentTimeMillis()
    Tools.withAttempt(3, 100.millis) {
      val n = calls.incrementAndGet()
      if (n < 3)
        throw new RuntimeException("transient")
      else
        "ok"
    }
    val elapsed = System.currentTimeMillis() - start
    // 2 retries × 100ms minimum. Allow generous slack for slow CI.
    assert(elapsed >= 200, s"elapsed = $elapsed ms")
  }
}
