package ru.stachek66.nlp.mystem.model

import org.json.JSONObject

/**
 * alexeyev 
 * 31.08.14.
 */
case class Info(initial: String, lex: Option[String], rawJson: JSONObject)
//, weight: Double) //, grInfo: GrammarInfo)