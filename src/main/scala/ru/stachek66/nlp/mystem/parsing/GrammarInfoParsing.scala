package ru.stachek66.nlp.mystem.parsing

import ru.stachek66.nlp.mystem.model._

/**
 * alexeyev 
 * 31.08.14.
 */
object GrammarInfoParsing {

  /**
   * Grammar info parsing.
   */
  def toGrammarInfo(commaSeparatedTags: String): GrammarInfo = {

    val mappedEnums =
      (commaSeparatedTags
        .split("[,=]")
        .map {
        case name: String =>
          val obj: Enumeration = GrammarMapBuilder.tagToEnumMap(name)
          (obj, obj.withName(name))
      } groupBy {
        case (obj: Enumeration, _) => obj
      } mapValues {
        case array => array.map(_._2)
      }).toMap

    def findByEnum[T <: scala.Enumeration](enum: T): Set[T#Value] =
      mappedEnums
        .get(enum)
        .map(_.map(_.asInstanceOf[T#Value]).toSet)
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
