package ru.stachek66.nlp.mystem.parsing

import org.json.JSONArray
import ru.stachek66.nlp.mystem.model.Info

/**
 * alexeyev 
 * 31.08.14.
 */
object JsonRepresentationParser {
  //todo:

  def toInfo(json: String): Traversable[Info] = toInfo(new JSONArray(json))

  //todo:
  private def toInfo(json: JSONArray): Traversable[Info] = {
    val stuff: Traversable[Info] =
      for (i <- 0 to json.length - 1)
      yield {
        val item = json.getJSONObject(i)
        val initial = item.getString("text")
        val analysis = item.getJSONArray("analysis")

        if (analysis.length() == 0)
          Info(initial, None)
        else {
          val result =
            for (j <- 0 to analysis.length - 1)
            yield {
              val anItem = analysis.getJSONObject(j)
              new Info(initial, Option(anItem.getString("lex"))) //,
                //          0d, GrammarInfoParsing.toGrammarInfo(anItem.getString("gr"))
            }
          //todo:
          result.head
        }
      }
    stuff
  }

  //todo:
  private def toJson(info: Info): JSONArray = throw new NotImplementedError

  def toJsonString(info: Info): String = toJson(info).toString

}