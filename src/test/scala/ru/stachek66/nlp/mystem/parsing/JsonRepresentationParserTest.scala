package ru.stachek66.nlp.mystem.parsing

import org.scalatest.funsuite.AnyFunSuite

class JsonRepresentationParserTest extends AnyFunSuite {

  test("a token with one analysis entry yields the lex of that entry") {
    val json = """[{"analysis":[{"lex":"мама","wt":1,"gr":"S,f,inan=nom,sg"}],"text":"мама"}]"""
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 1)
    assert(out.head.initial === "мама")
    assert(out.head.lex === Some("мама"))
  }

  test("a token with multiple homonym analyses keeps the first one") {
    // mystem returns several analyses for ambiguous tokens; the wrapper has
    // historically taken the first, and downstream callers depend on that.
    val json =
      """[{"analysis":[
        |  {"lex":"стать","wt":0.5,"gr":"V,impf=inf"},
        |  {"lex":"сталь","wt":0.5,"gr":"S,f,inan=nom,sg"}
        |],"text":"стать"}]""".stripMargin
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 1)
    assert(out.head.lex === Some("стать"))
  }

  test("a token with an empty analysis array yields no lex") {
    val json = """[{"analysis":[],"text":"qwertyuiop"}]"""
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 1)
    assert(out.head.initial === "qwertyuiop")
    assert(out.head.lex === None)
  }

  test("a token without an `analysis` key (e.g. punctuation) yields no lex") {
    val json = """[{"text":". "}]"""
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 1)
    assert(out.head.initial === ". ")
    assert(out.head.lex === None)
  }

  test("a multi-token response preserves order and per-token analyses") {
    val json =
      """[
        |  {"analysis":[{"lex":"я","wt":1,"gr":"SPRO,1p,sg=nom"}],"text":"я"},
        |  {"text":" "},
        |  {"analysis":[{"lex":"идти","wt":1,"gr":"V,ipf,intr=praes,sg,1p,indic"}],"text":"иду"}
        |]""".stripMargin
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.size === 3)
    assert(out(0).lex === Some("я"))
    assert(out(1).lex === None)
    assert(out(2).lex === Some("идти"))
  }

  test("rawResponse on each Info round-trips the source object") {
    val json = """[{"analysis":[{"lex":"кот","wt":1,"gr":"S,m,anim=nom,sg"}],"text":"кот"}]"""
    val out = JsonRepresentationParser.toInfo(json).toVector
    assert(out.head.rawResponse.contains("\"text\":\"кот\""))
    assert(out.head.rawResponse.contains("\"lex\":\"кот\""))
  }
}
