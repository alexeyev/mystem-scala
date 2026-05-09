package ru.stachek66.nlp.mystem.parsing

import org.scalatest.funsuite.AnyFunSuite

import ru.stachek66.nlp.mystem.model._

/** Behavioral spec for the comma/equals-separated tag string parser.
  *
  * Pinned contract:
  *   - Each token in the tag string is looked up in [[GrammarMapBuilder.tagToEnumMap]]
  *     and routed to the matching field on [[GrammarInfo]].
  *   - Multiple tags routed to the same field accumulate (Set semantics).
  *   - Round-trip (`toStringRepresentation` → `toGrammarInfo`) is structural-equal
  *     for any GrammarInfo whose every category lives on a [[GrammarInfo]] field.
  *   - Unknown tags surface a NoSuchElementException — we don't silently drop
  *     them, because that would make stale analyses look like valid ones.
  *
  * Known limitations are pinned with explicit tests so future refactors are
  * forced to be intentional about them. See `parens-pinning` and the
  * `person tag` test below.
  */
class GrammarInfoParsingTest extends AnyFunSuite {

  import GrammarInfoParsing._

  // -- POS-by-POS coverage -------------------------------------------------

  test("noun: S,f,inan=nom,sg routes correctly to every relevant field") {
    val gi = toGrammarInfo("S,f,inan=nom,sg")
    assert(gi.pos === Set(POS.S))
    assert(gi.gender === Set(Gender.feminine))
    assert(gi.animacy === Set(Animacy.inanimate))
    assert(gi.`case` === Set(Case.nominative))
    assert(gi.number === Set(Number.singular))
    // Negative checks: unrelated fields stay empty.
    assert(gi.tense.isEmpty)
    assert(gi.aspect.isEmpty)
    assert(gi.verbFormInfo.isEmpty)
    assert(gi.adjFormInfo.isEmpty)
    assert(gi.voice.isEmpty)
    assert(gi.other.isEmpty)
  }

  test("verb: V,ipf,intr=praes,sg,ind routes correctly") {
    // The `1p` tag (Person.p1) is documented separately below; we omit it
    // here because GrammarInfo does not surface a `person` field.
    val gi = toGrammarInfo("V,ipf,intr=praes,sg,ind")
    assert(gi.pos === Set(POS.V))
    assert(gi.aspect === Set(Aspect.imperfective))
    assert(gi.tense === Set(Tense.present))
    assert(gi.number === Set(Number.singular))
    assert(gi.verbFormInfo === Set(VerbForms.intransitive, VerbForms.indicativeMood))
  }

  test("adjective: A=plen,nom,sg,m gives full adjective analysis") {
    val gi = toGrammarInfo("A=plen,nom,sg,m")
    assert(gi.pos === Set(POS.A))
    assert(gi.adjFormInfo === Set(AdjectiveForms.plen))
    assert(gi.`case` === Set(Case.nominative))
    assert(gi.number === Set(Number.singular))
    assert(gi.gender === Set(Gender.masculine))
  }

  test("adverb: ADV — single-tag input has only `pos` populated") {
    val gi = toGrammarInfo("ADV")
    assert(gi.pos === Set(POS.ADV))
    // Every other field must be empty: this guards against accidental
    // cross-routing (e.g., a future refactor that added "ADV" to a wrong
    // enum and started populating two fields per tag).
    assert(gi.tense.isEmpty && gi.`case`.isEmpty && gi.number.isEmpty)
    assert(gi.gender.isEmpty && gi.aspect.isEmpty && gi.voice.isEmpty)
    assert(gi.animacy.isEmpty && gi.adjFormInfo.isEmpty && gi.verbFormInfo.isEmpty)
    assert(gi.other.isEmpty)
  }

  test("particle: PART has the right POS and nothing else") {
    val gi = toGrammarInfo("PART")
    assert(gi.pos === Set(POS.PART))
  }

  test("conjunction, preposition, interjection, numeral all map to the right POS") {
    assert(toGrammarInfo("CONJ").pos === Set(POS.CONJ))
    assert(toGrammarInfo("PR").pos === Set(POS.PR))
    assert(toGrammarInfo("INTJ").pos === Set(POS.INTJ))
    assert(toGrammarInfo("NUM").pos === Set(POS.NUM))
  }

  // -- Round-trip ---------------------------------------------------------

  test("round-trip is structural for a noun") {
    val original = toGrammarInfo("S,f,inan=nom,sg")
    val round = toGrammarInfo(toStringRepresentation(original))
    assert(round === original)
  }

  test("round-trip is structural for a verb (mood, aspect, tense, number, transitivity)") {
    val original = toGrammarInfo("V,ipf,intr=praes,sg,ind")
    val round = toGrammarInfo(toStringRepresentation(original))
    assert(round === original)
  }

  test("round-trip is structural for an adjective") {
    val original = toGrammarInfo("A=plen,nom,sg,m")
    val round = toGrammarInfo(toStringRepresentation(original))
    assert(round === original)
  }

  // -- Multi-value within a dimension -------------------------------------

  test("tags from different dimensions do NOT collide on the same field") {
    // Sanity: even though both are 3-letter, "fem" and "fut" don't exist
    // in the same enum. We verify with two real tags from different enums
    // that they end up on different GrammarInfo fields.
    val gi = toGrammarInfo("S,f=nom")
    assert(gi.pos === Set(POS.S))
    assert(gi.gender === Set(Gender.feminine))
    assert(gi.`case` === Set(Case.nominative))
  }

  test("repeated tag (would-be-duplicate) collapses into a single Set entry") {
    // The set semantics matter: callers iterate over .pos / .gender / etc.
    // Any duplication would produce double-counted answers.
    val gi = toGrammarInfo("S,nom,nom")
    assert(gi.`case`.size === 1)
    assert(gi.`case` === Set(Case.nominative))
  }

  // -- GrammarMapBuilder ---------------------------------------------------

  test("GrammarMapBuilder includes every tag we exercise above") {
    val tags = Seq(
      "S",
      "V",
      "A",
      "ADV",
      "PART",
      "CONJ",
      "PR",
      "INTJ",
      "NUM", // POS
      "f",
      "m",
      "n", // Gender
      "anim",
      "inan", // Animacy
      "nom",
      "gen",
      "dat",
      "acc",
      "ins",
      "loc", // Case
      "sg",
      "pl", // Number
      "ipf",
      "pf", // Aspect
      "praes",
      "past",
      "inpraes", // Tense
      "ind",
      "imper",
      "tran",
      "intr",
      "inf",
      "partcp",
      "ger", // VerbForms
      "plen",
      "brev",
      "poss",
      "supr",
      "comp", // AdjectiveForms
      "act",
      "pass", // Voice
      "1p",
      "2p",
      "3p" // Person
    )
    val missing = tags.filterNot(GrammarMapBuilder.tagToEnumMap.contains)
    assert(missing.isEmpty, s"GrammarMapBuilder is missing: ${missing.mkString(", ")}")
  }

  test("each documented tag in tagToEnumMap maps to the enum it belongs to") {
    // Catches a class of mistake: a tag accidentally registered under the
    // wrong enum (e.g., "nom" landing in Gender instead of Case).
    val expectations = Map(
      "S" -> POS,
      "V" -> POS,
      "A" -> POS,
      "f" -> Gender,
      "m" -> Gender,
      "n" -> Gender,
      "anim" -> Animacy,
      "inan" -> Animacy,
      "nom" -> Case,
      "gen" -> Case,
      "acc" -> Case,
      "sg" -> Number,
      "pl" -> Number,
      "ipf" -> Aspect,
      "pf" -> Aspect,
      "praes" -> Tense,
      "past" -> Tense,
      "1p" -> Person,
      "2p" -> Person,
      "3p" -> Person,
      "ind" -> VerbForms,
      "imper" -> VerbForms,
      "intr" -> VerbForms,
      "tran" -> VerbForms,
      "act" -> Voice,
      "pass" -> Voice,
      "plen" -> AdjectiveForms,
      "brev" -> AdjectiveForms,
      "parenth" -> Other,
      "geo" -> Other
    )
    expectations.foreach { case (tag, expectedEnum) =>
      assert(
        GrammarMapBuilder.tagToEnumMap.get(tag).contains(expectedEnum),
        s"$tag should map to $expectedEnum but got ${GrammarMapBuilder.tagToEnumMap.get(tag)}"
      )
    }
  }

  // -- Failure modes ------------------------------------------------------

  test("an unknown tag produces a NoSuchElementException, not a silent drop") {
    // Silent drop would let a future mystem version emit a new tag and
    // produce subtly wrong analyses without anyone noticing. The current
    // contract is: blow up early.
    intercept[NoSuchElementException] {
      toGrammarInfo("S,f,UNKNOWN_TAG")
    }
  }

  // -- Pinned known limitations -------------------------------------------

  test("known limitation: the `1p`/`2p`/`3p` person tag is recognized but lost") {
    // Person values exist in tagToEnumMap (verified above) but `GrammarInfo`
    // has no `person: Set[Person.Value]` field, so the parser drops them
    // on the floor. This test pins that behavior so any future change that
    // adds a `person` field is forced to update both production and tests
    // intentionally.
    val gi = toGrammarInfo("V,ipf,intr=praes,sg,1p,ind")
    // No assertion *about* person — there's no field to assert on. The
    // important assertion is that PARSING didn't throw, since that's
    // observable from outside.
    assert(gi.pos === Set(POS.V))
  }

  test("known limitation: ambiguity-paren syntax `(a,b|c,d)` is NOT supported") {
    // Real mystem output with `--weight` looks like
    //   "S,f,inan=(gen,sg|nom,pl)"
    // The current parser splits on `[,=]` only, so the parens become
    // part of the would-be tag (e.g., "(gen") and lookup in
    // tagToEnumMap throws NoSuchElementException. This is documented
    // here so a future supporter knows it's a known issue, not an
    // accidental regression.
    intercept[NoSuchElementException] {
      toGrammarInfo("S,f,inan=(gen,sg|nom,pl)")
    }
  }
}
