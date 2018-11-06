package ru.stachek66.nlp.mystem.holding

import ru.stachek66.nlp.mystem.model.Info
import ru.stachek66.nlp.mystem.parsing.JsonRepresentationParser
import ru.stachek66.tools.external.FailSafeExternalProcessServer

import scala.util.{Failure, Success}

/**
 * alexeyev 
 * 16.10.14.
 */
case class Request(text: String)

case class Response(info: Traversable[Info])

trait MyStem {

  def normalize(text: String): String = text.replaceAll("\n", " ")

  @throws(classOf[MyStemApplicationException])
  def analyze(request: Request): Response
}

class MyStemApplicationException(e: Throwable) extends java.lang.Exception

// We need this because mystem.v < 3.0 doesn't support json AFAIK
class MyStem30 private[holding](s: FailSafeExternalProcessServer) extends MyStem {

  @throws(classOf[MyStemApplicationException])
  override def analyze(request: Request): Response = {
    s.syncRequest(normalize(request.text)) match {
      case Failure(e) => throw new MyStemApplicationException(e)
      case Success(json) => Response(JsonRepresentationParser.toInfo(json))
    }
  }
}