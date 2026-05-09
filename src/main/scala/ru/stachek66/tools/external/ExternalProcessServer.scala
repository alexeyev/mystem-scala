package ru.stachek66.tools.external

import java.io.{BufferedReader, BufferedWriter, InputStreamReader, OutputStreamWriter}
import java.nio.charset.StandardCharsets

import org.slf4j.LoggerFactory

import scala.util.Try

/** Thin synchronous wrapper around an external CLI process that speaks
  * one-line-request / one-line-response over stdin/stdout.
  *
  * Lifecycle notes:
  *  - The process is started lazily-ish (in the constructor); use [[isAlive]]
  *    to check status.
  *  - [[close]] destroys the process. It is safe to call multiple times and
  *    is what the JVM shutdown hook in [[FailSafeExternalProcessServer]]
  *    invokes to fix the long-standing "mystem doesn't terminate after main
  *    thread quits" issue.
  *  - Reader/writer wrap the streams in UTF-8; this is the encoding mystem
  *    uses for its JSON output.
  */
private[external] class ExternalProcessServer(starterCommand: String) extends SyncServer {

  private val log = LoggerFactory.getLogger(getClass)

  // Tokenize on whitespace the same way Runtime.exec(String) used to. We
  // switch to ProcessBuilder so that we can reliably inherit STDERR (mystem
  // sometimes writes diagnostics there) and keep the parent JVM in charge of
  // the child's lifecycle.
  private val command: Array[String] = starterCommand.trim.split("\\s+")

  private val process: Process = {
    val pb = new ProcessBuilder(java.util.Arrays.asList(command: _*))
    pb.redirectErrorStream(false)
    pb.start()
  }

  private val writer: BufferedWriter =
    new BufferedWriter(new OutputStreamWriter(process.getOutputStream, StandardCharsets.UTF_8), 1)

  private val reader: BufferedReader =
    new BufferedReader(new InputStreamReader(process.getInputStream, StandardCharsets.UTF_8))

  override def syncRequest(request: String): Try[String] = Try {

    writer.write(request)
    writer.newLine()
    writer.flush()

    // Block until the first byte of the response is available — or the
    // process exits without producing one. BufferedReader.ready() actually
    // returns FALSE at EOF (because the underlying InputStream's
    // available() returns 0 once the pipe is closed), so without the
    // explicit process.isAlive check the wrapper would spin here
    // indefinitely after a child crash. mystem is robust enough that this
    // doesn't trigger in practice, but a segfault or OOM in the child
    // shouldn't permanently jam the wrapper either.
    while (!reader.ready() && process.isAlive) {}

    // Drain whatever's available. readLine() returns null at EOF; we
    // stop appending in that case so we don't accidentally emit the
    // literal string "null" as part of the response.
    val builder = new StringBuilder()
    var atEof = false
    while (reader.ready() && !atEof) {
      val line = reader.readLine()
      if (line == null)
        atEof = true
      else
        builder.append(line)
    }

    // If the process exited without producing any output, surface that
    // as a Failure (via the surrounding Try) rather than returning an
    // empty string — MyStem3.analyze would otherwise interpret that as
    // a successful empty analysis and skip the FailSafe restart path.
    if (builder.isEmpty && (atEof || !process.isAlive)) {
      throw new java.io.IOException(
        "process exited before producing any response"
      )
    }

    builder.toString()
  }

  def isAlive: Boolean = process.isAlive

  override def close(): Unit = {
    val _ = Try(writer.close())
    val _2 = Try(reader.close())
    if (process.isAlive) {
      process.destroy()
      // Give the child a brief, bounded grace period; force-kill if it
      // refuses to exit. 1 second is plenty for `mystem` to flush state.
      try {
        if (!process.waitFor(1L, java.util.concurrent.TimeUnit.SECONDS)) {
          log.warn("Process [{}] did not exit gracefully; forcibly destroying.", command(0))
          process.destroyForcibly()
        }
      } catch {
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
          process.destroyForcibly()
      }
    }
  }

}
