package ru.stachek66.nlp.mystem.parsing

import ru.stachek66.nlp.mystem.model._

/** Parser for mystem's comma-separated grammatical-tag strings.
  *
  * mystem emits two related shapes in its `gr` field:
  *
  *   - Single-analysis form: `S,m,inan=nom,sg`. One unambiguous parse.
  *
  *   - Multi-analysis form with mutually-exclusive alternatives:
  *     `A,plen=(acc,sg,m,anim|gen,sg,m|gen,sg,n)`. Tags before `=(...)`
  *     apply to every alternative; the alternatives are separated by `|`
  *     inside the parens. This is the format mystem 3.x emits whenever the
  *     wrapper passes `--weight` (which the default `Factory` does), so any
  *     production caller will see it.
  *
  * Use `toGrammarInfos` to get every alternative as its own [[GrammarInfo]];
  * use `toGrammarInfo` for the single-most-likely parse (mystem orders
  * alternatives by descending probability, so `.head` is "most likely").
  */
object GrammarInfoParsing {

  /** Captures `<fixed>=(<alternatives>)` where:
    *   - group 1 includes the trailing `=` so we can prefix it onto each
    *     alternative without further surgery;
    *   - group 2 is the `|`-separated alternatives, possibly empty.
    *
    * Anchored to start/end to avoid matching parens that aren't the
    * top-level alternatives wrapper. We use `[^)]*` rather than `.*` so a
    * stray `(` deeper in the string doesn't get pulled in by greedy match.
    */
  private val parensPattern = """^(.*=)\(([^)]*)\)$""".r

  /** Parse mystem's tag string into one [[GrammarInfo]] per parens-alternative.
    *
    * For non-parens input the result is a single-element list — callers can
    * always work with `List[GrammarInfo]` and not special-case the simple
    * shape.
    */
  def toGrammarInfos(commaSeparatedTags: String): List[GrammarInfo] =
    commaSeparatedTags match {
      case parensPattern(prefix, inner) =>
        // `inner.split('|')` returns Array("") for an empty inner string,
        // which gives us a single GrammarInfo built from just the prefix —
        // that's the right behavior for the degenerate `S=()` shape.
        inner.split('|').iterator.map(alt => parseSingleAnalysis(prefix + alt)).toList
      case _ =>
        List(parseSingleAnalysis(commaSeparatedTags))
    }

  /** Parse the most-likely interpretation as a single [[GrammarInfo]].
    *
    * For non-parens input, identical to the old behavior. For parens input,
    * returns the first alternative — mystem orders alternatives by
    * descending probability, so this is the "best guess" interpretation.
    *
    * Use [[toGrammarInfos]] when you need every alternative.
    */
  def toGrammarInfo(commaSeparatedTags: String): GrammarInfo =
    toGrammarInfos(commaSeparatedTags).head

  /** Parse a flat (non-parens) tag string. The previous implementation
    * used `mapValues`, which in Scala 2.13 returns a lazy `MapView` and
    * emits a deprecation warning under `-Xlint`. We use `Map#map` directly,
    * which is well-defined and identical in semantics on both 2.12 and 2.13.
    */
  private def parseSingleAnalysis(commaSeparatedTags: String): GrammarInfo = {

    val mappedEnums: Map[Enumeration, Vector[Enumeration#Value]] = commaSeparatedTags
      .split("[,=]")
      .iterator
      .map { name =>
        // Look up the enum by the wire-format name (alias or canonical),
        // but compute the actual Value via withName(canonical(name)) —
        // Scala's Enumeration#withName has no notion of aliases.
        val obj: Enumeration = GrammarMapBuilder.tagToEnumMap(name)
        (obj, obj.withName(GrammarMapBuilder.canonical(name)))
      }
      .toVector
      .groupBy { case (obj, _) => obj }
      .map { case (k, pairs) => k -> pairs.map { case (_, v) => v } }

    def findByEnum[T <: scala.Enumeration](enum: T): Set[T#Value] = mappedEnums
      .get(enum)
      .map(_.iterator.map(_.asInstanceOf[T#Value]).toSet)
      .getOrElse(Set.empty[T#Value])

    GrammarInfo(
      pos = findByEnum(POS),
      tense = findByEnum(Tense),
      `case` = findByEnum(Case),
      number = findByEnum(Number),
      person = findByEnum(Person),
      verbFormInfo = findByEnum(VerbForms),
      adjFormInfo = findByEnum(AdjectiveForms),
      gender = findByEnum(Gender),
      aspect = findByEnum(Aspect),
      voice = findByEnum(Voice),
      animacy = findByEnum(Animacy),
      other = findByEnum(Other)
    )
  }

  /** Re-emit a [[GrammarInfo]] as a flat comma-separated tag string.
    *
    * This is intentionally lossy: it does NOT reproduce the
    * `prefix=variable` or parens-alternative structure of the original
    * input — those are wire-format concerns of mystem, not of the parsed
    * model. It does include every set value across every dimension, in a
    * form that round-trips through [[toGrammarInfo]] for any single
    * (non-alternative) analysis.
    */
  def toStringRepresentation(gi: GrammarInfo): String =
    (gi.`case` ++ gi.adjFormInfo ++ gi.animacy ++ gi.aspect ++ gi.gender ++
      gi.number ++ gi.person ++ gi.pos ++ gi.other ++ gi.tense ++
      gi.verbFormInfo ++ gi.voice).mkString(",")

}
