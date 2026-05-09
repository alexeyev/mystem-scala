package ru.stachek66.nlp.mystem.holding

import org.scalatest.funsuite.AnyFunSuite

import ru.stachek66.nlp.mystem.model.Info

/** Direct, in-process tests for the parts of [[MyStem]] /
  * [[MyStemApplicationException]] that don't need a real binary. The
  * end-to-end behavior is covered by [[MyStem3IntegrationTest]] when a
  * binary is provided; the contract pinned here is the input/output
  * machinery around the subprocess call, which is otherwise easy to
  * regress unnoticed.
  */
class CommunicationTest extends AnyFunSuite {

  /** Trivial in-memory stub so we don't need a real subprocess. */
  private class StubMyStem(echo: Request => Response = r => Response(Vector.empty)) extends MyStem {
    override def analyze(request: Request): Response = echo(request)
  }

  // -- MyStem.normalize ---------------------------------------------------

  test("normalize replaces a single newline with a space") {
    val m = new StubMyStem()
    assert(m.normalize("foo\nbar") === "foo bar")
  }

  test("normalize replaces every newline, not just the first") {
    // Why this matters: mystem speaks one-request-per-line over stdin. If
    // any `\n` survives in the request text, mystem will treat the
    // remainder as a brand-new request and the response stream desyncs.
    // A "first newline only" implementation would limp along on most
    // inputs and explode on multi-paragraph ones.
    val m = new StubMyStem()
    assert(m.normalize("a\nb\nc\nd") === "a b c d")
  }

  test("normalize on a string with no newline is identity") {
    val m = new StubMyStem()
    val text = "никаких переносов строк"
    assert(m.normalize(text) === text)
  }

  test("normalize preserves carriage returns (does NOT touch \\r)") {
    // Pinning current behavior: the protocol delimiter is `\n` only, and
    // mystem itself handles bare CRs in input. If we ever decide to strip
    // CRs too, this test forces that change to be intentional.
    val m = new StubMyStem()
    assert(m.normalize("foo\rbar") === "foo\rbar")
  }

  test("normalize handles empty input") {
    val m = new StubMyStem()
    assert(m.normalize("") === "")
  }

  test("normalize accepts long input without truncation") {
    // No length cap is documented; pin that defensively.
    val m = new StubMyStem()
    val long = ("слово\n" * 1000).stripSuffix("\n")
    val out = m.normalize(long)
    assert(out.length === long.length)
    assert(!out.contains('\n'), "every newline must be replaced")
  }

  // -- MyStemApplicationException ----------------------------------------

  test("MyStemApplicationException carries the original cause") {
    // We need callers to be able to switch on `e.getCause` to distinguish
    // closed-state errors (IllegalStateException), I/O errors, etc. — so
    // the cause has to survive the wrap.
    val cause = new IllegalStateException("the wrapped reason")
    val ex = new MyStemApplicationException(cause)
    assert(ex.getCause eq cause, "cause must be the exact same object, not a copy")
    assert(ex.getCause.isInstanceOf[IllegalStateException])
  }

  test("MyStemApplicationException's message mirrors the cause's message") {
    // Convenience: when the exception is logged or printed without
    // unwrapping, the user should still see what went wrong.
    val cause = new RuntimeException("subprocess died")
    val ex = new MyStemApplicationException(cause)
    assert(ex.getMessage === "subprocess died")
  }

  test("MyStemApplicationException on a null cause has a null message and survives toString") {
    // Defensive: the wrapper handles `null` so callers don't NPE on edge
    // cases like a destroy-during-syncRequest race that leaves the Try
    // failure with a null cause.
    val ex = new MyStemApplicationException(null)
    assert(ex.getCause === null)
    assert(ex.getMessage === null)
    // Most importantly, toString must not blow up.
    assert(ex.toString !== null)
  }

  test("MyStemApplicationException is a checked exception (extends java.lang.Exception)") {
    // The reason it's checked: Java callers should be forced to handle it
    // (or declare it). If a future refactor accidentally extends
    // RuntimeException, Java users would silently miss the failure.
    val ex = new MyStemApplicationException(new RuntimeException("x"))
    assert(ex.isInstanceOf[java.lang.Exception])
    // Reflection avoids "fruitless type test" lint when scalac can prove
    // the unchecked path is statically impossible — we still want a
    // runtime-observable assertion in case the class hierarchy changes.
    assert(
      !classOf[RuntimeException].isAssignableFrom(classOf[MyStemApplicationException]),
      "must NOT be unchecked"
    )
  }

  // -- MyStem.close default ------------------------------------------------

  test("MyStem.close has a no-op default for trait implementers") {
    // Pinning this for future implementers who don't own a subprocess —
    // they shouldn't have to override close() just to say "nothing to do".
    val m = new StubMyStem()
    m.close()
    m.close() // multiple calls fine
  }

  // -- Response (a couple of fast in-process checks) ----------------------

  test("Response is a value-equality case class") {
    // Sanity: the wrapper relies on case-class semantics for diffing
    // analysis results in tests.
    val a = Response(Vector(Info("x", Some("x"), "")))
    val b = Response(Vector(Info("x", Some("x"), "")))
    assert(a === b)
    val c = Response(Vector(Info("y", Some("y"), "")))
    assert(a !== c)
  }
}
