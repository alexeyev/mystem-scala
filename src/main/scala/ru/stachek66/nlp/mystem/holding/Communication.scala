package ru.stachek66.nlp.mystem.holding

import java.util
import ru.stachek66.nlp.mystem.model.Info
import ru.stachek66.nlp.mystem.parsing.JsonRepresentationParser
import ru.stachek66.tools.external.FailSafeExternalProcessServer

import scala.util.{Failure, Success}

/**
 * Domain objects exchanged with [[MyStem]].
 *
 * Original author: alexeyev (16.10.14).
 */
case class Request(text: String)

/**
 * The result of a single analyze() call. The `info` field is a Scala
 * `Iterable` (NOT the long-deprecated `Traversable`, which has been removed
 * in Scala 3). Java callers should prefer `getInfoAsList()` to avoid
 * touching Scala collections at all.
 */
case class Response(info: Iterable[Info]) {

  /** Java-friendly view of [[info]]; backed by a fresh ArrayList. */
  def getInfoAsList: util.List[Info] = {
    val out = new util.ArrayList[Info]()
    info.foreach(out.add)
    out
  }
}

/**
 * Public service interface. Implementations own a long-lived child process
 * and MUST be `close()`d when no longer needed; otherwise the underlying
 * mystem binary will outlive the JVM on shutdown — see
 * https://github.com/alexeyev/mystem-scala/issues/3 .
 *
 * `MyStem extends java.lang.AutoCloseable` so Java users can wrap instances
 * in a try-with-resources block.
 */
trait MyStem extends AutoCloseable {

  def normalize(text: String): String = text.replace('\n', ' ')

  @throws(classOf[MyStemApplicationException])
  def analyze(request: Request): Response

  /** Default no-op so simple implementations don't have to override. */
  override def close(): Unit = ()
}

/**
 * Wraps any throwable that escapes the underlying mystem subprocess into a
 * checked exception so Java callers can catch it explicitly.
 */
class MyStemApplicationException(cause: Throwable)
    extends java.lang.Exception(if (cause != null) cause.getMessage else null, cause)

/**
 * Implementation for mystem 3.0 / 3.1, which speak JSON. Earlier mystem
 * versions used a different output format and are not supported.
 */
class MyStem3 private[holding] (s: FailSafeExternalProcessServer) extends MyStem {

  @throws(classOf[MyStemApplicationException])
  override def analyze(request: Request): Response =
    s.syncRequest(normalize(request.text)) match {
      case Failure(e)    => throw new MyStemApplicationException(e)
      case Success(json) => Response(JsonRepresentationParser.toInfo(json))
    }

  override def close(): Unit = s.close()
}
