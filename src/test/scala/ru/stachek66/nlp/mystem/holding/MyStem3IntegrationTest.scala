package ru.stachek66.nlp.mystem.holding

import java.io.File

import org.scalatest.funsuite.AnyFunSuite

import ru.stachek66.nlp.mystem.model.{AdjectiveForms, Number, POS}
import ru.stachek66.nlp.mystem.parsing.GrammarInfoParsing

/** End-to-end integration test that drives the actual `mystem` binary.
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

  private def assumeBinary(): Unit = assume(
    binary.isDefined,
    "set -Dmystem.binary=<path> or MYSTEM_BINARY=<path> to enable integration tests"
  )

  /** Construct a fresh [[MyStem3]] for one test and reliably tear it down.
    * Per-test instances give sharper diagnostics when something fails.
    */
  private def withMystem[A](f: MyStem => A): A = {
    val m = new Factory().newMyStem("3.1", binary).get
    try f(m)
    finally m.close()
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

  // -- Parens-pipe alternatives in real output ---------------------------

  test("[integration] real verb output with `indic`/`praet` aliases parses every dimension") {
    // Pre-fix, real verb output threw NoSuchElementException on `indic`
    // (mystem's wire form for indicative mood; our enum had `ind`) and
    // `praet` (past tense; our enum had `past`). We added alias
    // canonicalisation so real output is parseable. End-to-end test.
    assumeBinary()
    withMystem { m =>
      // Present-tense, 1st-person-singular indicative.
      val present = m.analyze(Request("читаю")).info.toVector
      val rawP = present.head.rawResponse
      val grP = "\"gr\":\"([^\"]+)\"".r.findFirstMatchIn(rawP).map(_.group(1)).get
      val giP = GrammarInfoParsing.toGrammarInfo(grP)
      assert(giP.pos === Set(POS.V), s"gr=$grP")
      assert(
        giP.verbFormInfo.contains(ru.stachek66.nlp.mystem.model.VerbForms.indicativeMood),
        s"`indic` alias must map to indicativeMood, gr=$grP, parsed=$giP"
      )
      assert(giP.person.nonEmpty, s"verb in 1p form must have a person tag, gr=$grP")

      // Past-tense form, exercises `praet` alias.
      val past = m.analyze(Request("мыл")).info.toVector
      val rawPast = past.find(_.lex.contains("мыть")).get.rawResponse
      val grPast = "\"gr\":\"([^\"]+)\"".r.findFirstMatchIn(rawPast).map(_.group(1)).get
      val giPast = GrammarInfoParsing.toGrammarInfo(grPast)
      assert(
        giPast.tense === Set(ru.stachek66.nlp.mystem.model.Tense.past),
        s"`praet` alias must map to Tense.past, gr=$grPast, parsed=$giPast"
      )
    }
  }

  test("[integration] real mystem parens-pipe gr fields parse into multiple GrammarInfos") {
    // The lemma "тестового" has three mutually exclusive parses:
    //   acc.sg.m.anim | gen.sg.m | gen.sg.n
    // mystem emits this as a parens-pipe `gr` string, which used to throw
    // NoSuchElementException because `(acc` was looked up as a tag. Now
    // toGrammarInfos splits on `|` and returns one GrammarInfo per
    // alternative. We don't depend on a fixed alternative count (mystem
    // could conceivably tighten or expand the analysis in a future
    // release); we DO depend on every alternative being well-formed.
    assumeBinary()
    withMystem { m =>
      val infos = m.analyze(Request("тестового")).info.toVector
      val raw = infos.head.rawResponse
      assert(raw.contains("("), s"expected parens-syntax `gr` in real output, got:\n$raw")
      val grRegex = "\"gr\":\"([^\"]+)\"".r
      val gr = grRegex.findFirstMatchIn(raw).map(_.group(1)).getOrElse(fail(s"no gr field in $raw"))

      val parsed = GrammarInfoParsing.toGrammarInfos(gr)
      assert(parsed.size >= 2, s"expected ≥2 alternatives in real output `$gr`, got ${parsed.size}")

      // Every alternative shares the prefix-derived dimensions: POS=A,
      // adjFormInfo=plen. Verifying these holds across alternatives proves
      // the prefix was applied to each.
      parsed.foreach { gi =>
        assert(gi.pos === Set(POS.A))
        assert(gi.adjFormInfo === Set(AdjectiveForms.plen))
        assert(gi.number === Set(Number.singular))
      }

      // No alternative can have empty `case` — every parse must commit to
      // one of acc/gen/etc. This catches a class of bug where the prefix
      // is applied but the alternative-specific tags are dropped.
      parsed.foreach(gi => assert(gi.`case`.nonEmpty, s"alternative with empty case: $gi"))
    }
  }
}
