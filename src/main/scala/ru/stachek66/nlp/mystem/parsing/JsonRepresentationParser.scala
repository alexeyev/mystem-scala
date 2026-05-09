package ru.stachek66.nlp.mystem.parsing

import org.json.JSONArray
import ru.stachek66.nlp.mystem.model.Info

/** Parses mystem's `--format json` output into [[Info]] objects.
  *
  * The signature switched from the deprecated `Traversable` to `Iterable` to
  * make the wrapper compile cleanly under Scala 2.13's strict deprecation
  * settings and stay source-compatible with Scala 3.
  */
object JsonRepresentationParser {

  def toInfo(json: String): Iterable[Info] = toInfo(new JSONArray(json))

  private def toInfo(json: JSONArray): Iterable[Info] = {

    val out = Vector.newBuilder[Info]

    var i = 0
    while (i < json.length()) {
      val item = json.getJSONObject(i)
      val initial = item.getString("text")

      if (item.has("analysis")) {
        val analysis = item.getJSONArray("analysis")
        if (analysis.length() == 0) {
          out += Info(initial, None, item.toString)
        } else {
          // mystem may return multiple homonym analyses for a single token;
          // we keep the first one to match the previous library behavior.
          val anItem = analysis.getJSONObject(0)
          out += Info(initial, Option(anItem.getString("lex")), item.toString)
        }
      } else {
        out += Info(initial, None, item.toString)
      }

      i += 1
    }

    out.result()
  }

}
