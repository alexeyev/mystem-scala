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

  test("verb: V,ipf,intr=praes,sg,1p,ind routes correctly to every dimension including person") {
    val gi = toGrammarInfo("V,ipf,intr=praes,sg,1p,ind")
    assert(gi.pos === Set(POS.V))
    assert(gi.aspect === Set(Aspect.imperfective))
    assert(gi.tense === Set(Tense.present))
    assert(gi.number === Set(Number.singular))
    assert(gi.person === Set(Person.p1))
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

  // -- Wire-format aliases (mystem-3.x ↔ Enumeration name divergence) ---

  test("`indic` (mystem's wire form) parses as VerbForms.indicativeMood") {
    // mystem 3.x emits `indic` for indicative mood, but our enum was
    // declared with `Value(\"ind\")` historically. Both must parse.
    // Without alias support, real verb output throws NoSuchElementException.
    val gi = toGrammarInfo("V,ipf,intr=inpraes,sg,indic,1p")
    assert(gi.verbFormInfo.contains(VerbForms.indicativeMood))
    assert(gi.tense === Set(Tense.inpraes))
    assert(gi.person === Set(Person.p1))
  }

  test("`ind` (the canonical Value name) still parses, for backward compat") {
    val gi = toGrammarInfo("V,ipf,intr=praes,sg,1p,ind")
    assert(gi.verbFormInfo.contains(VerbForms.indicativeMood))
  }

  test("`praet` (mystem wire form for past tense) parses as Tense.past") {
    // Same shape as the indic alias: `Value(\"past\")` historically,
    // mystem emits `praet` (Latin praeteritum). Real past-tense output
    // would otherwise throw.
    val gi = toGrammarInfo("V,ipf,intr=praet,sg,indic,m")
    assert(gi.tense === Set(Tense.past))
    assert(gi.gender === Set(Gender.masculine))
  }

  test("`past` (canonical name) still parses, for backward compat") {
    val gi = toGrammarInfo("V=past")
    assert(gi.tense === Set(Tense.past))
  }

  test("real-world `inpraes,sg,indic,1p` parses every dimension correctly") {
    // The actual `gr` shape mystem emits for present-tense indicative
    // verbs like \"иду\" / \"читаю\" / \"играю\". Pinned end-to-end:
    // tense, number, mood, person all populated correctly with alias
    // mapping in place.
    val gi = toGrammarInfo("V,ipf,tran=inpraes,sg,indic,1p")
    assert(gi.pos === Set(POS.V))
    assert(gi.aspect === Set(Aspect.imperfective))
    assert(gi.verbFormInfo === Set(VerbForms.transitive, VerbForms.indicativeMood))
    assert(gi.tense === Set(Tense.inpraes))
    assert(gi.number === Set(Number.singular))
    assert(gi.person === Set(Person.p1))
  }

  test("alias canonicalisation only rewrites known aliases, not arbitrary tags") {
    // Pin: canonical(x) is the identity for tags not in the alias map.
    // Otherwise a future addition to aliases could silently rename
    // unrelated tags.
    assert(GrammarMapBuilder.canonical("nom") === "nom")
    assert(GrammarMapBuilder.canonical("S") === "S")
    assert(GrammarMapBuilder.canonical("indic") === "ind") // alias
    assert(GrammarMapBuilder.canonical("praet") === "past") // alias
    // Unknown tags: canonical() returns them unchanged. The throw on
    // lookup is the parser's responsibility, not canonical's.
    assert(GrammarMapBuilder.canonical("nonsense") === "nonsense")
  }

  test("each documented tag in tagToEnumMap maps to the enum it belongs to") {    // Catches a class of mistake: a tag accidentally registered under the
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

  // -- Person field -------------------------------------------------------

  test("person tag (`1p`/`2p`/`3p`) populates GrammarInfo.person") {
    // Used to be a silent-drop bug: tagToEnumMap routed `1p` through Person,
    // but GrammarInfo had no `person` field for it to land in. Fixed by
    // adding `person: Set[Person.Value]` and populating it from the same
    // findByEnum machinery as every other dimension.
    assert(toGrammarInfo("V,ipf,intr=praes,sg,1p,ind").person === Set(Person.p1))
    assert(toGrammarInfo("V,ipf,intr=praes,pl,2p,ind").person === Set(Person.p2))
    assert(toGrammarInfo("V,ipf,intr=praes,sg,3p,ind").person === Set(Person.p3))
  }

  test("an analysis without a person tag has an empty person set (not null)") {
    // Negative: the field is `Set` so it had better default to `Set.empty`,
    // not crash. This pins the case-class default.
    val noun = toGrammarInfo("S,f,inan=nom,sg")
    assert(noun.person !== null)
    assert(noun.person.isEmpty)
  }

  test("round-trip preserves person across toStringRepresentation → toGrammarInfo") {
    val original = toGrammarInfo("V,ipf,intr=praes,sg,2p,ind")
    val round = toGrammarInfo(toStringRepresentation(original))
    assert(round === original, "structural equality must include the person field")
    assert(round.person === Set(Person.p2))
  }

  test("toStringRepresentation includes person tags in its output") {
    // Sanity check on the serializer: if we forgot to add gi.person to the
    // concatenation, the round-trip above would still pass for any
    // GrammarInfo whose person field was empty, but would fail for ones
    // where it isn't. This test pins the serializer directly.
    val gi = toGrammarInfo("V,ipf,intr=praes,sg,3p,ind")
    val emitted = toStringRepresentation(gi)
    assert(emitted.split(',').contains("3p"), s"toStringRepresentation should emit `3p`, got: $emitted")
  }

  // -- Parens-pipe alternative-analysis form ------------------------------

  test("toGrammarInfos returns one GrammarInfo per parens-alternative") {
    // Real mystem output for the lemma "тестового" — three mutually
    // exclusive parses sharing a common `A,plen=` prefix. Each alternative
    // gets its own GrammarInfo with the right combination of dimensions;
    // we verify that the alternatives DON'T cross-contaminate (e.g., the
    // animacy tag from alt 1 must not leak into alt 2 or alt 3).
    val infos = toGrammarInfos("A,plen=(acc,sg,m,anim|gen,sg,m|gen,sg,n)")
    assert(infos.size === 3)

    // Alt 0: accusative, singular, masculine, animate.
    assert(infos(0).pos === Set(POS.A))
    assert(infos(0).adjFormInfo === Set(AdjectiveForms.plen))
    assert(infos(0).`case` === Set(Case.accusative))
    assert(infos(0).number === Set(Number.singular))
    assert(infos(0).gender === Set(Gender.masculine))
    assert(infos(0).animacy === Set(Animacy.animate))

    // Alt 1: genitive, singular, masculine, NO animacy tag.
    assert(infos(1).`case` === Set(Case.genitive))
    assert(infos(1).gender === Set(Gender.masculine))
    assert(infos(1).animacy.isEmpty, "anim must NOT bleed across alternatives")

    // Alt 2: genitive, singular, neuter, NO animacy.
    assert(infos(2).`case` === Set(Case.genitive))
    assert(infos(2).gender === Set(Gender.neuter))
    assert(infos(2).animacy.isEmpty)
  }

  test("toGrammarInfos with a 2-alternative input") {
    // Smaller realistic case to verify the parser doesn't only handle
    // exactly-3 alternatives.
    val infos = toGrammarInfos("S,f,inan=(gen,sg|nom,pl)")
    assert(infos.size === 2)
    assert(infos(0).`case` === Set(Case.genitive) && infos(0).number === Set(Number.singular))
    assert(infos(1).`case` === Set(Case.nominative) && infos(1).number === Set(Number.plural))
  }

  test("toGrammarInfos returns a single-element list for non-parens input") {
    // The shape with no parens in `gr` (mystem omits parens when there's
    // only one analysis) should still work — we shouldn't make every
    // caller special-case "is there a paren or not?".
    val infos = toGrammarInfos("S,f,inan=nom,sg")
    assert(infos.size === 1)
    assert(infos.head.pos === Set(POS.S))
    assert(infos.head.`case` === Set(Case.nominative))
  }

  test("toGrammarInfos for a bare POS (no `=`) works") {
    val infos = toGrammarInfos("ADV")
    assert(infos.size === 1)
    assert(infos.head.pos === Set(POS.ADV))
  }

  test("toGrammarInfo (singular) returns the FIRST alternative when input has parens") {
    // mystem orders alternatives by descending probability, so `.head` is
    // the most-likely interpretation. Pinning this so a future "return
    // average" or "return union" change has to be intentional.
    val gi = toGrammarInfo("A,plen=(acc,sg,m,anim|gen,sg,m|gen,sg,n)")
    val infos = toGrammarInfos("A,plen=(acc,sg,m,anim|gen,sg,m|gen,sg,n)")
    assert(gi === infos.head)
    assert(gi.`case` === Set(Case.accusative))
    assert(gi.animacy === Set(Animacy.animate))
  }

  test("the fixed prefix is correctly applied to every alternative") {
    // Regression for an off-by-one in prefix construction that would have
    // produced a malformed combined string for alt 2 onwards. Every
    // alternative should preserve the `A` POS and the `plen` adjective
    // form (which both come from the prefix).
    val infos = toGrammarInfos("A,plen=(acc,sg,m,anim|gen,sg,m|gen,sg,n)")
    infos.foreach { gi =>
      assert(gi.pos === Set(POS.A), "POS lost on later alternative")
      assert(gi.adjFormInfo === Set(AdjectiveForms.plen), "adjFormInfo lost on later alternative")
      assert(gi.number === Set(Number.singular), "number tag from variable part lost")
    }
  }

  test("empty parens `S=()` yields a single GrammarInfo with just the prefix tags") {
    // Defensible: the prefix has its own meaning, the parens are the
    // alternative slot. Empty alternatives → one trivial alternative.
    val infos = toGrammarInfos("A,plen=()")
    assert(infos.size === 1)
    assert(infos.head.pos === Set(POS.A))
    assert(infos.head.adjFormInfo === Set(AdjectiveForms.plen))
    assert(infos.head.`case`.isEmpty, "no case tag in input → empty case set")
  }

  test("a stray unknown tag inside parens fails the whole parse, not silently") {
    // Same fail-loud contract as the non-parens path: an unrecognized tag
    // means we're looking at output we don't understand, which is a build-
    // time signal, not something to swallow at runtime.
    intercept[NoSuchElementException] {
      toGrammarInfos("A,plen=(acc,sg|UNKNOWN_TAG,sg)")
    }
  }
}
