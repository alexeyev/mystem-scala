package ru.stachek66.nlp.mystem.model

/**
 * alexeyev 
 * 01.09.14.
 */
case class GrammarInfo(pos: Set[POS.Value] = Set.empty,
                       tense: Set[Tense.Value] = Set.empty,
                       `case`: Set[Case.Value] = Set.empty,
                       number: Set[Number.Value] = Set.empty,
                       verbFormInfo: Set[VerbForms.Value] = Set.empty[VerbForms.Value],
                       adjFormInfo: Set[AdjectiveForms.Value] = Set.empty[AdjectiveForms.Value],
                       gender: Set[Gender.Value] = Set.empty,
                       aspect: Set[Aspect.Value] = Set.empty,
                       voice: Set[Voice.Value] = Set.empty,
                       animacy: Set[Animacy.Value] = Set.empty,
                       other: Set[Other.Value] = Set.empty[Other.Value])
