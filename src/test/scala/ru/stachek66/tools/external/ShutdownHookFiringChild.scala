package ru.stachek66.tools.external

/**
 * Companion main used by [[ShutdownHookFiringTest]] to verify that the JVM
 * shutdown hook actually destroys the spawned process when the JVM exits
 * without an explicit `close()`.
 *
 * Lives in the test sources, so the test classpath we hand to the forked
 * JVM is sufficient to find it.
 *
 * Protocol:
 *   - argv(0) = path to a tiny shell script that writes its own PID to a
 *     file and then `exec`s `sleep`.
 *   - argv(1) = path to the PID file the script will write.
 *
 * Behavior:
 *   1. Construct a [[FailSafeExternalProcessServer]] pointing at the script.
 *      The script's `exec sleep` keeps the same PID, so the PID file ends
 *      up holding the live `sleep`-process PID.
 *   2. Wait briefly for the PID file to appear.
 *   3. Print `PID=<n>` on stdout and exit normally — without calling
 *      `close()` on the server. The shutdown hook is the *only* thing
 *      that should kill the spawned process.
 */
object ShutdownHookFiringChild {

  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      System.err.println("Usage: ShutdownHookFiringChild <scriptPath> <pidFilePath>")
      System.exit(2)
    }
    val scriptPath = args(0)
    val pidFile = new java.io.File(args(1))

    // Construct the server. The `starterCommand` is "<scriptPath> <pidFilePath>"
    // because the wrapper splits its single string argument on whitespace
    // and passes the resulting array as separate process arguments.
    val server = new FailSafeExternalProcessServer(s"$scriptPath ${pidFile.getAbsolutePath}")

    // Wait up to 5s for the script to write its PID. Generous because some
    // CI hosts are slow to fork a shell.
    val deadline = System.currentTimeMillis() + 5000
    while (!pidFile.exists() && System.currentTimeMillis() < deadline) {
      Thread.sleep(25L)
    }
    if (!pidFile.exists()) {
      System.err.println("script did not write its PID")
      System.exit(3)
    }

    val pid = {
      val src = scala.io.Source.fromFile(pidFile)
      try src.getLines().mkString.trim
      finally src.close()
    }
    println(s"PID=$pid")
    System.out.flush()

    // Deliberately DO NOT call server.close(). The shutdown hook is what
    // the parent test is verifying.
    val _ = server
  }
}
