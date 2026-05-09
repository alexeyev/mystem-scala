package ru.stachek66.nlp.mystem.parsing

import org.json.JSONException
import org.scalatest.funsuite.AnyFunSuite

import ru.stachek66.nlp.mystem.model.Info

/** Behavioral spec for [[JsonRepresentationParser.toInfo]].
  *
  * The parser consumes the JSON line that mystem 3.x emits per `analyze`
  * round-trip and produces one [[Info]] per surface token. The contract:
  *
  *   - Each token contributes exactly one Info; ordering is preserved.
  *   - When `analysis` is absent or empty, `lex` is `None`.
  *   - When `analysis` has multiple entries (homonyms), the FIRST one's lex
  *     is taken — downstream callers depend on this for compatibility.
  *   - `rawResponse` carries the JSON for that token, not the whole line.
  *   - Malformed JSON surfaces a JSONException; we don't try to "recover"
  *     because doing so would silently lose tokens.
  *   - The returned Iterable is strict (re-iterable, not lazy).
  */
class JsonRepresentationParserTest extends AnyFunSuite {

  // -- Happy-path single-token cases --------------------------------------

  test("a single analysed token yields its lex") {
    val json = """[{"analysis":[{"lex":"мама","wt":1,"gr":"S,f,inan=nom,sg"}],"text":"мама"}]"""
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 1)
    assert(out.head.initial === "мама")
    assert(out.head.lex === Some("мама"))
  }

  test("multiple homonym analyses → the FIRST analysis's lex is selected") {
    // mystem orders analyses by descending weight, so "first" === most likely.
    // Several callers (lemmatization pipelines, search indexers) rely on
    // this; if we ever switch to "all" or "random", that's a breaking change
    // and this test will scream.
    val json =
      """[{"analysis":[
        |  {"lex":"стать","wt":0.7,"gr":"V,impf=inf"},
        |  {"lex":"сталь","wt":0.3,"gr":"S,f,inan=nom,sg"}
        |],"text":"стать"}]""".stripMargin
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 1)
    assert(out.head.lex === Some("стать"))
    assert(out.head.lex !== Some("сталь"))
  }

  test("empty analysis array → lex is None (initial is preserved)") {
    val json = """[{"analysis":[],"text":"qwertyuiop"}]"""
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 1)
    assert(out.head.initial === "qwertyuiop")
    assert(out.head.lex === None)
  }

  test("token without an `analysis` key (whitespace, punctuation) → lex is None") {
    val json = """[{"text":". "}]"""
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 1)
    assert(out.head.initial === ". ")
    assert(out.head.lex === None)
  }

  // -- Multi-token / ordering ---------------------------------------------

  test("multi-token response preserves order and per-token analyses") {
    val json =
      """[
        |  {"analysis":[{"lex":"я","wt":1,"gr":"SPRO,1p,sg=nom"}],"text":"я"},
        |  {"text":" "},
        |  {"analysis":[{"lex":"идти","wt":1,"gr":"V,ipf,intr=praes,sg,1p,indic"}],"text":"иду"}
        |]""".stripMargin
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 3)
    assert(out(0) === Info("я", Some("я"), out(0).rawResponse))
    assert(out(1).initial === " " && out(1).lex === None)
    assert(out(2) === Info("иду", Some("идти"), out(2).rawResponse))
  }

  test("an empty top-level array yields an empty Iterable, not a failure") {
    val out = JsonRepresentationParser.toInfo("[]").toVector
    assert(out.isEmpty)
  }

  // -- rawResponse ---------------------------------------------------------

  test("rawResponse on each Info carries that token's JSON object only") {
    val json =
      """[
        |  {"analysis":[{"lex":"кот","wt":1,"gr":"S,m,anim=nom,sg"}],"text":"кот"},
        |  {"analysis":[{"lex":"спать","wt":1,"gr":"V,ipf,intr=praes,sg,3p,indic"}],"text":"спит"}
        |]""".stripMargin
    val out = JsonRepresentationParser.toInfo(json).toVector

    // First Info's rawResponse mentions "кот" but NOT "спит" — proves the
    // raw is per-token, not the whole array.
    assert(out(0).rawResponse.contains("\"text\":\"кот\""))
    assert(out(0).rawResponse.contains("\"lex\":\"кот\""))
    assert(!out(0).rawResponse.contains("спит"), "rawResponse must scope to one token")

    assert(out(1).rawResponse.contains("\"text\":\"спит\""))
    assert(!out(1).rawResponse.contains("кот"))
  }

  // -- Iterability --------------------------------------------------------

  test("the result is a strict Iterable: it can be traversed twice") {
    // Strictness matters: a `Stream`-like return would consume process
    // resources lazily and is harder to reason about. We rely on a Vector.
    val json = """[{"analysis":[{"lex":"a","wt":1,"gr":""}],"text":"a"}]"""
    val out = JsonRepresentationParser.toInfo(json)
    val first = out.toVector
    val second = out.toVector
    assert(first === second, "iterating twice must yield the same elements")
    assert(first.nonEmpty)
  }

  // -- Cyrillic / encoding ------------------------------------------------

  test("Cyrillic round-trips byte-for-byte through Info.initial and Info.lex") {
    // Regression guard: a wrong charset somewhere on the read path would
    // mojibake this string ("?????" or "Ð¼Ð°Ð¼Ð°"). We deliberately use
    // characters across the basic Cyrillic block.
    val word = "ёжик-непослушный-жмурится"
    val json = s"""[{"analysis":[{"lex":"$word","wt":1,"gr":"A"}],"text":"$word"}]"""
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.head.initial === word)
    assert(out.head.lex === Some(word))
  }

  // -- Realistic sample ---------------------------------------------------

  test("a realistic mystem-3.1 line with weights/gr fields parses end-to-end") {
    // This is the actual JSON shape `mystem -igd --eng-gr --format json --weight`
    // emits. We don't assert on every field — we assert what the wrapper
    // is contractually responsible for: token count, lex selection per
    // token, and that none of the unrecognized fields trip us up.
    val realistic =
      """[
        |  {"analysis":[{"lex":"мама","wt":0.999988,"gr":"S,f,inan=(gen,sg|acc,pl|nom,pl|nom,sg)"}],"text":"мама"},
        |  {"text":" "},
        |  {"analysis":[{"lex":"мыть","wt":0.999851,"gr":"V,impf,tran=praes,sg,3p,indic"}],"text":"мыла"},
        |  {"text":" "},
        |  {"analysis":[{"lex":"рама","wt":0.999990,"gr":"S,f,inan=(gen,sg|acc,pl|nom,pl|nom,sg)"}],"text":"раму"}
        |]""".stripMargin
    val out = JsonRepresentationParser.toInfo(realistic).toVector
    assert(out.map(_.initial) === Vector("мама", " ", "мыла", " ", "раму"))
    assert(out.flatMap(_.lex) === Vector("мама", "мыть", "рама"))
  }

  // -- Failure modes ------------------------------------------------------

  test("malformed JSON surfaces a JSONException — we don't silently swallow") {
    intercept[JSONException] {
      JsonRepresentationParser.toInfo("[{not-valid-json}]")
    }
  }

  test("a non-array top-level (object instead of array) is also a JSONException") {
    intercept[JSONException] {
      JsonRepresentationParser.toInfo("""{"text":"oops"}""")
    }
  }
}
