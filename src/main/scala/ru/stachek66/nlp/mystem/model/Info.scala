package ru.stachek66.nlp.mystem.model

import ru.stachek66.nlp.mystem.parsing.GrammarInfoParsing

/**
 * alexeyev 
 * 31.08.14.
 */
case class Info(initial: String, lex: String, weight: Double, grInfo: GrammarInfo)