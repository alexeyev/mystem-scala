package ru.stachek66.nlp.mystem.model

/** alexeyev
  * 31.08.14.
  */
object GrammarMapBuilder {

  /** Aliases between the wire-format tag names mystem 3.x actually emits
    * and the canonical names hard-coded into the [[Enumeration]] objects
    * below. mystem's docs and its real output diverge — `mystem -igd`
    * emits `indic` for indicative mood (we declared `ind`) and `praet`
    * for past tense (we declared `past`). The pre-3.x naming was kept
    * for backward compatibility of `Value.toString` output, but real
    * output has to parse, so we map alias → canonical here.
    *
    * Public so callers who manually post-process tag strings can use the
    * same canonicalisation rules as the parser.
    */
  val aliases: Map[String, String] = Map(
    "indic" -> "ind",   // VerbForms.indicativeMood
    "praet" -> "past"   // Tense.past
  )

  /** The canonical (Enumeration-Value-side) form of a wire-format tag. */
  def canonical(tag: String): String = aliases.getOrElse(tag, tag)

  /** Tag-string → enclosing Enumeration. Includes both the canonical
    * names (every Value emitted by every Enumeration below) AND every
    * alias entry from [[aliases]] mapped to the canonical name's enum.
    */
  lazy val tagToEnumMap: Map[String, Enumeration] = {
    val canonicalMap: Map[String, Enumeration] =
      (tagToEnum(POS) ++ tagToEnum(Tense) ++ tagToEnum(Animacy) ++
        tagToEnum(Aspect) ++ tagToEnum(VerbForms) ++ tagToEnum(Gender) ++
        tagToEnum(Number) ++ tagToEnum(Voice) ++ tagToEnum(Other) ++
        tagToEnum(AdjectiveForms) ++ tagToEnum(Person) ++ tagToEnum(Case)).toMap

    canonicalMap ++ aliases.flatMap {
      case (alias, canon) => canonicalMap.get(canon).map(alias -> _)
    }
  }

  private def tagToEnum(enum: Enumeration): Set[(String, Enumeration)] =
    // `values.iterator.toSet` is cross-version-safe; `values.unsorted` was
    // a 2.13-only addition that breaks the Scala 2.12 build.
    enum.values.iterator.map(value => value.toString -> enum).toSet

}

object POS extends Enumeration {
  val A = Value("A")
  val ADV = Value("ADV")
  val CONJ = Value("CONJ")
  val INTJ = Value("INTJ")
  val NUM = Value("NUM")
  val PART = Value("PART")
  val PR = Value("PR")
  val S = Value("S")
  val V = Value("V")
}

object Tense extends Enumeration {
  val present = Value("praes")
  val inpraes = Value("inpraes")
  val past = Value("past")
}

object Case extends Enumeration {
  val nominative = Value("nom")
  val genitive = Value("gen")
  val dative = Value("dat")
  val accusative = Value("acc")
  val vocative = Value("voc")
  val instrumental = Value("ins")

  val ablative = Value("abl")
  val locative = Value("loc")
  val partitive = Value("part")
}

object Number extends Enumeration {
  val plural = Value("pl")
  val singular = Value("sg")
}

object VerbForms extends Enumeration {
  val transgressive = Value("ger")
  val infinitive = Value("inf")
  val participle = Value("partcp")

  val indicativeMood = Value("ind")
  val imperativeMood = Value("imper")

  val transitive = Value("tran")
  val intransitive = Value("intr")
}

object AdjectiveForms extends Enumeration {
  val brev = Value("brev")
  val plen = Value("plen")
  val possessive = Value("poss")
  val supreme = Value("supr")
  val comparative = Value("comp")
}

object Person extends Enumeration {
  val p1 = Value("1p")
  val p2 = Value("2p")
  val p3 = Value("3p")
}

object Gender extends Enumeration {
  val feminine = Value("f")
  val masculine = Value("m")
  val neuter = Value("n")
}

object Aspect extends Enumeration {
  val perfective = Value("pf")
  val imperfective = Value("ipf")
}

object Voice extends Enumeration {
  val active = Value("act")
  val passive = Value("pass")
}

object Animacy extends Enumeration {
  val animate = Value("anim")
  val inanimate = Value("inan")
}

object Other extends Enumeration {
  // вводное слово
  val parenth = Value("parenth")
  val geo = Value("geo")
  // образование формы затруднено
  val awkward = Value("awkw")
  val personal = Value("persn")
  val distorted = Value("dist")
  // общая форма мужского и женского рода
  val mf = Value("mf")
  val obscene = Value("obsc")
  val patrn = Value("patrn")
  // предикатив
  val praedicative = Value("praed")
  // разговорная форма
  val informal = Value("inform")
  val rare = Value("rare")
  val abbr = Value("abbr")
  val obsolete = Value("obsol")
  val familyName = Value("famn")
}
