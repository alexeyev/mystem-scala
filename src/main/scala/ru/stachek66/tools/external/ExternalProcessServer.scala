package ru.stachek66.tools.external

import java.io.{BufferedReader, BufferedWriter, OutputStreamWriter}
import java.nio.charset.Charset

import org.slf4j.LoggerFactory

import scala.util._


/**
 * Please be careful when using! No death handling.
 * alexeyev 
 * 13.09.14.
 */
private[external] class ExternalProcessServer(starterCommand: String) extends SyncServer {

  private val log = LoggerFactory.getLogger(getClass)

  private val p = Runtime.getRuntime.exec(starterCommand)
  private val (in, out, err) = (p.getInputStream, p.getOutputStream, p.getErrorStream)

  private val writer = new BufferedWriter(new OutputStreamWriter(out, Charset.forName("utf-8")), 1)
  private val reader = io.Source.fromInputStream(in).reader()
  private val bufferedReader = new BufferedReader(reader)

  def syncRequest(request: String): Try[String] = Try {

    writer.write(request)
    writer.newLine()
    writer.flush()

    while (!bufferedReader.ready()) {}

    val builder = new StringBuilder()
    while (bufferedReader.ready) builder.append(bufferedReader.readLine())
    builder.toString()
  }

  def isAlive: Boolean = {
    Try(p.exitValue()) match {
      case Success(_) => false
      case Failure(e: IllegalThreadStateException) => true
      case Failure(e) => throw new RuntimeException(e) // unknown exception
    }
  }

  def kill() {
    p.destroy()
  }
}