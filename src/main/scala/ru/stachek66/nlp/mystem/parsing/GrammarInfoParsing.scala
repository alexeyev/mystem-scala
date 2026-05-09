package ru.stachek66.nlp.mystem.parsing

import ru.stachek66.nlp.mystem.model._

object GrammarInfoParsing {

  /**
   * Parse mystem's comma-separated tag string into a typed [[GrammarInfo]].
   *
   * The previous implementation used `mapValues`, which in Scala 2.13 returns
   * a lazy `MapView` and emits a deprecation warning under `-Xlint`. We now
   * use `Map#map` directly, which is well-defined and identical in semantics
   * on both Scala 2.12 and 2.13.
   */
  def toGrammarInfo(commaSeparatedTags: String): GrammarInfo = {

    val mappedEnums: Map[Enumeration, Vector[Enumeration#Value]] =
      commaSeparatedTags
        .split("[,=]")
        .iterator
        .map { name =>
          val obj: Enumeration = GrammarMapBuilder.tagToEnumMap(name)
          (obj, obj.withName(name))
        }
        .toVector
        .groupBy { case (obj, _) => obj }
        .map { case (k, pairs) => k -> pairs.map { case (_, v) => v } }

    def findByEnum[T <: scala.Enumeration](enum: T): Set[T#Value] =
      mappedEnums
        .get(enum)
        .map(_.iterator.map(_.asInstanceOf[T#Value]).toSet)
        .getOrElse(Set.empty[T#Value])

    GrammarInfo(
      pos = findByEnum(POS),
      tense = findByEnum(Tense),
      `case` = findByEnum(Case),
      number = findByEnum(Number),
      verbFormInfo = findByEnum(VerbForms),
      adjFormInfo = findByEnum(AdjectiveForms),
      gender = findByEnum(Gender),
      aspect = findByEnum(Aspect),
      voice = findByEnum(Voice),
      animacy = findByEnum(Animacy),
      other = findByEnum(Other)
    )
  }

  def toStringRepresentation(gi: GrammarInfo): String =
    (gi.`case` ++ gi.adjFormInfo ++ gi.animacy ++ gi.aspect ++ gi.gender ++
      gi.number ++ gi.pos ++ gi.other ++ gi.tense ++ gi.verbFormInfo ++ gi.voice).mkString(",")
}
