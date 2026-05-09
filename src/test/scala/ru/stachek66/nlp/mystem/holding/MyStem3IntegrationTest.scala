package ru.stachek66.nlp.mystem.holding

import java.io.File

import org.scalatest.funsuite.AnyFunSuite

import ru.stachek66.nlp.mystem.parsing.GrammarInfoParsing

/**
 * End-to-end integration test that drives the actual `mystem` binary.
 *
 * Opt-in: point the test at a binary via either
 *   - the `MYSTEM_BINARY` environment variable, or
 *   - the `mystem.binary` JVM system property (takes precedence).
 *
 * If neither is set, every test in this class is skipped via `assume`.
 * To run locally:
 *
 *   `mvn -Dmystem.binary=/abs/path/to/mystem test`
 *
 * Why an integration test on top of unit tests? Two specific properties
 * only manifest end-to-end:
 *   - The JSON we feed [[ru.stachek66.nlp.mystem.parsing.JsonRepresentationParser]]
 *     in unit tests really matches what mystem emits under the
 *     `-igd --eng-gr --format json --weight` flags (the wrapper's defaults).
 *     Note that mystem's default behaviour DROPS punctuation/whitespace
 *     entirely; the `-c` flag would preserve them as `{"text":" "}` tokens.
 *     We test the default flag set, which is what most callers use.
 *   - The line-buffering/process-lifecycle loop in
 *     [[ru.stachek66.tools.external.ExternalProcessServer]] terminates
 *     correctly against a real CLI rather than only against an echo loop.
 */
class MyStem3IntegrationTest extends AnyFunSuite {

  /** Resolve the binary path (system property → env var → None). */
  private val binary: Option[File] = {
    val fromProp = Option(System.getProperty("mystem.binary"))
    val fromEnv = Option(System.getenv("MYSTEM_BINARY"))
    fromProp.orElse(fromEnv).map(new File(_)).filter(_.canExecute)
  }

  private def assumeBinary(): Unit =
    assume(
      binary.isDefined,
      "set -Dmystem.binary=<path> or MYSTEM_BINARY=<path> to enable integration tests"
    )

  /**
   * Construct a fresh [[MyStem3]] for one test and reliably tear it down.
   * Per-test instances give sharper diagnostics when something fails.
   */
  private def withMystem[A](f: MyStem => A): A = {
    val m = new Factory().newMyStem("3.1", binary).get
    try f(m) finally m.close()
  }

  // -- Smoke ---------------------------------------------------------------

  test("[integration] analyze returns one Info per word, with the expected lex") {
    assumeBinary()
    withMystem { m =>
      val out = m.analyze(Request("проверка тестового запуска")).info.toVector
      assert(out.map(_.initial) === Vector("проверка", "тестового", "запуска"))
      assert(out.flatMap(_.lex) === Vector("проверка", "тестовый", "запуск"))
    }
  }

  test("[integration] punctuation is dropped under the default flag set; only words remain") {
    // Default mystem flags `-igd --eng-gr --format json --weight` strip
    // commas, exclamation marks, and whitespace. This isn't a wrapper
    // choice — it's mystem's own default. Pinning it here guards against
    // a future flag-tweak in Factory that quietly changes tokenization.
    assumeBinary()
    withMystem { m =>
      val out = m.analyze(Request("Привет, мир!")).info.toVector
      assert(out.map(_.initial) === Vector("Привет", "мир"))
      assert(out.forall(_.lex.isDefined), "every emitted token under defaults must have a lex")
    }
  }

  test("[integration] empty input produces an empty analysis") {
    assumeBinary()
    withMystem { m =>
      val out = m.analyze(Request("")).info.toVector
      assert(out.isEmpty, s"expected empty result, got: ${out.map(_.initial)}")
    }
  }

  test("[integration] newlines are normalized to spaces (no input-line splitting)") {
    // The wrapper's normalize() replaces \n with a space so the protocol
    // (one request per line over stdin) doesn't desync. Verify that the
    // multi-line and space-joined inputs produce the same lemmas.
    assumeBinary()
    withMystem { m =>
      val nl = m.analyze(Request("мама\nмыла\nраму")).info.toVector
      val spc = m.analyze(Request("мама мыла раму")).info.toVector
      assert(nl.flatMap(_.lex) === spc.flatMap(_.lex))
      assert(nl.flatMap(_.lex) === Vector("мама", "мыть", "рама"))
    }
  }

  // -- Cross-component contract -------------------------------------------

  test("[integration] rawResponse from real mystem is the per-token JSON object the unit tests assume") {
    assumeBinary()
    withMystem { m =>
      val infos = m.analyze(Request("кот спит")).info.toVector
      assert(infos.exists(_.lex.contains("кот")))
      assert(infos.exists(_.lex.contains("спать")))
      // Per-token isolation: each rawResponse mentions its own surface
      // form, not the others. This pins the contract that
      // JsonRepresentationParser unit-tests against synthetic input.
      val koshka = infos.find(_.lex.contains("кот")).get
      val spat = infos.find(_.lex.contains("спать")).get
      assert(koshka.rawResponse.contains("\"text\":\"кот\""))
      assert(!koshka.rawResponse.contains("спит"), "per-token rawResponse must not bleed across tokens")
      assert(spat.rawResponse.contains("\"text\":\"спит\""))
    }
  }

  // -- Lifecycle pinned end-to-end ----------------------------------------

  test("[integration] close() prevents further analyze calls (state machine guard)") {
    assumeBinary()
    val m = new Factory().newMyStem("3.1", binary).get
    val first = m.analyze(Request("кот")).info.toVector
    assert(first.flatMap(_.lex) === Vector("кот"))

    m.close()

    // After close(), analyze must NOT silently spawn a hookless replacement
    // (re-introducing issue #3). The contract: the IllegalStateException
    // from FailSafeExternalProcessServer.syncRequest travels through
    // Try.Failure and surfaces as a MyStemApplicationException whose
    // cause is the IllegalStateException — uniform error channel.
    val ex = intercept[MyStemApplicationException] {
      m.analyze(Request("ещё раз"))
    }
    assert(
      ex.getCause.isInstanceOf[IllegalStateException],
      s"expected IllegalStateException cause, got: ${ex.getCause}"
    )
  }

  // -- Reality check on the parens-ambiguity known limitation -------------

  test("[integration] real mystem output exposes the parens-ambiguity gr format we don't parse") {
    // Documentation-as-test. The user runs `mystem -igd --eng-gr --format
    // json --weight` and the `gr` field commonly looks like
    //   "A,plen=(acc,sg,m,anim|gen,sg,m|gen,sg,n)"
    // GrammarInfoParsing.toGrammarInfo can't handle the parens; this test
    // pins that the format actually does occur in real output AND that
    // the parser deliberately throws on it. If mystem's output ever
    // changes shape (or the parser learns to handle parens), this test
    // forces an intentional update.
    assumeBinary()
    withMystem { m =>
      val infos = m.analyze(Request("тестового")).info.toVector
      val raw = infos.head.rawResponse
      assert(raw.contains("("), s"expected parens-syntax `gr` in real output, got:\n$raw")
      val grRegex = "\"gr\":\"([^\"]+)\"".r
      val gr = grRegex.findFirstMatchIn(raw).map(_.group(1)).getOrElse(fail(s"no gr field in $raw"))

      intercept[NoSuchElementException] {
        GrammarInfoParsing.toGrammarInfo(gr)
      }
    }
  }
}
