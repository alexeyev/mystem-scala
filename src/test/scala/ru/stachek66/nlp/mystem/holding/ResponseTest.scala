package ru.stachek66.nlp.mystem.holding

import java.{util => ju}

import org.scalatest.funsuite.AnyFunSuite

import ru.stachek66.nlp.mystem.model.Info

/**
 * Behavioral spec for [[Response]] and its Java-friendly accessor.
 *
 * The wrapper used to leak `scala.collection.Traversable` to Java callers
 * (which then needed `scala.collection.JavaConversions` to consume it —
 * removed in Scala 2.13). [[Response.getInfoAsList]] is the supported
 * Java entry point; this spec pins its semantics so a future "let's
 * return a view" refactor can't silently break Java users.
 */
class ResponseTest extends AnyFunSuite {

  private def info(initial: String, lex: Option[String]): Info =
    Info(initial = initial, lex = lex, rawResponse = "")

  test("getInfoAsList returns a non-null List in source order, with all elements") {
    val infos = Vector(info("a", Some("a")), info("b", None), info("c", Some("c")))
    val response = Response(infos)

    val list: ju.List[Info] = response.getInfoAsList
    assert(list !== null)
    assert(list.size() === 3)
    assert(list.get(0) === infos(0))
    assert(list.get(1) === infos(1))
    assert(list.get(2) === infos(2))
  }

  test("getInfoAsList returns an empty (not null) list for an empty Response") {
    val response = Response(Vector.empty)
    val list = response.getInfoAsList
    assert(list !== null)
    assert(list.isEmpty)
  }

  test("the returned list is independent: mutating it does not affect the Response") {
    // Sharing a backing collection would surprise Java callers and break
    // referential transparency of the `info` accessor.
    val infos = Vector(info("a", Some("a")), info("b", Some("b")))
    val response = Response(infos)

    val list = response.getInfoAsList
    list.clear()
    assert(list.isEmpty)
    // Original Response.info is untouched.
    assert(response.info.size === 2)
    // A fresh call yields a fresh list with the original contents.
    val again = response.getInfoAsList
    assert(again.size() === 2)
  }

  test("getInfoAsList works when `info` is a non-Vector Iterable") {
    // The case class field type is `Iterable[Info]`; callers might pass
    // anything (Set, List, custom Iterable). The accessor must work on
    // all of them.
    val response = Response(Set(info("only", Some("one"))))
    val list = response.getInfoAsList
    assert(list.size() === 1)
    assert(list.get(0).initial === "only")
  }
}
