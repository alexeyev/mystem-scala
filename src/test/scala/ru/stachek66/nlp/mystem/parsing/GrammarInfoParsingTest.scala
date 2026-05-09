package ru.stachek66.nlp.mystem.parsing

import org.scalatest.funsuite.AnyFunSuite
import ru.stachek66.nlp.mystem.model._

class GrammarInfoParsingTest extends AnyFunSuite {

  import GrammarInfoParsing._

  test("a noun analysis splits into POS, case, number, gender, animacy") {
    val gi = toGrammarInfo("S,f,inan=nom,sg")
    assert(gi.pos === Set(POS.S))
    assert(gi.gender === Set(Gender.feminine))
    assert(gi.animacy === Set(Animacy.inanimate))
    assert(gi.`case` === Set(Case.nominative))
    assert(gi.number === Set(Number.singular))
    assert(gi.tense === Set.empty)
    assert(gi.verbFormInfo === Set.empty)
  }

  test("a verb analysis splits into POS, aspect, transitivity, tense, mood") {
    // mystem's tag for indicative mood is "ind" (see GrammarInfoParts.scala).
    val gi = toGrammarInfo("V,ipf,intr=praes,sg,1p,ind")
    assert(gi.pos === Set(POS.V))
    assert(gi.aspect === Set(Aspect.imperfective))
    assert(gi.tense === Set(Tense.present))
    assert(gi.number === Set(Number.singular))
    assert(gi.verbFormInfo.contains(VerbForms.intransitive))
    assert(gi.verbFormInfo.contains(VerbForms.indicativeMood))
  }

  test("toStringRepresentation/toGrammarInfo are mutually consistent for a noun") {
    val original = toGrammarInfo("S,f,inan=nom,sg")
    val round = toGrammarInfo(toStringRepresentation(original))
    // Equality on case classes is structural.
    assert(round === original)
  }

  test("the GrammarMapBuilder recognizes every documented tag") {
    // A reasonable smoke test: every tag the parser needs to handle for the
    // example above is in the map.
    val tags = Seq("S", "V", "f", "inan", "nom", "sg", "ipf", "intr", "praes", "ind")
    tags.foreach(t => assert(GrammarMapBuilder.tagToEnumMap.contains(t), s"missing tag: $t"))
  }
}
