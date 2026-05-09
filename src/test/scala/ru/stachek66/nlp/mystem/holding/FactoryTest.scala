package ru.stachek66.nlp.mystem.holding

import java.io.File

import org.scalatest.funsuite.AnyFunSuite

/** Tests for [[Factory]] paths that don't require network access or a real
  * mystem binary. The end-to-end happy path (download + unpack + spawn) is
  * exercised by [[MyStem3IntegrationTest]] when a binary is provided; here
  * we cover the version-validation logic and custom-executable handling.
  */
class FactoryTest extends AnyFunSuite {

  // -- Unsupported versions ----------------------------------------------

  test("newMyStem with an unsupported version returns Failure(NotImplementedError)") {
    // The earlier mystem 1.x / 2.x binaries used a different output format
    // (one-token-per-line, no JSON) that this wrapper does not parse. We
    // refuse to instantiate rather than letting a user discover the
    // mismatch via mojibake output. Failure is wrapped in `Try` so callers
    // can branch on it without a try/catch.
    val factory = new Factory()
    // Use a custom executable so we don't try to download anything during
    // the test — we want the version check to fail BEFORE I/O.
    val fakeBinary = File.createTempFile("factorytest-fake-", ".bin")
    fakeBinary.deleteOnExit()
    val _ = fakeBinary.setExecutable(true)

    val result = factory.newMyStem("2.0", Some(fakeBinary))
    assert(result.isFailure, "result must be Failure for unsupported version")
    val ex = result.failed.get
    assert(
      ex.isInstanceOf[NotImplementedError],
      s"expected NotImplementedError, got ${ex.getClass.getName}: ${ex.getMessage}"
    )
    assert(
      ex.getMessage.contains("2.0"),
      s"error must name the offending version. message: ${ex.getMessage}"
    )
  }

  test("newMyStem with an arbitrary garbage version is also rejected") {
    val factory = new Factory()
    val fakeBinary = File.createTempFile("factorytest-fake-", ".bin")
    fakeBinary.deleteOnExit()
    val _ = fakeBinary.setExecutable(true)

    val result = factory.newMyStem("not-a-version", Some(fakeBinary))
    assert(result.isFailure)
    assert(result.failed.get.isInstanceOf[NotImplementedError])
  }

  // -- Supported versions reach process spawn ----------------------------

  test("newMyStem with a supported version and a non-mystem binary still constructs (process spawn is lazy-ish)") {
    // Pin: the constructor of FailSafeExternalProcessServer DOES start the
    // process eagerly (via the wrapped ExternalProcessServer), so a totally
    // bogus binary path may surface a Failure here. We use /bin/cat (which
    // exists on every CI runner Linux/macOS) as a stand-in: it spawns,
    // doesn't crash, and we never call analyze() — we just verify the
    // happy-path version routing works. Skipped on hosts without it.
    assume(new File("/bin/cat").canExecute, "needs /bin/cat")
    val factory = new Factory(parsingOptions = "")
    val result = factory.newMyStem("3.1", Some(new File("/bin/cat")))
    assert(result.isSuccess, s"expected Success, got $result")
    val mystem = result.get
    try assert(mystem.isInstanceOf[MyStem3], "supported version must yield MyStem3")
    finally mystem.close()
  }

  test("the parsingOptions argument is appended to the command line") {
    // The default `Factory()` constructor uses `-igd --eng-gr --format
    // json --weight`. Other callers can override; pin that the override
    // is actually used (otherwise mystem would default to its own
    // text-output format and the wrapper's JSON parser would break).
    assume(new File("/bin/cat").canExecute, "needs /bin/cat")
    val factory = new Factory(parsingOptions = "--my-custom-flag")
    // Even with bogus flags, /bin/cat will accept and ignore them on its
    // command line for the purposes of starting up.
    val result = factory.newMyStem("3.1", Some(new File("/bin/cat")))
    assert(result.isSuccess)
    result.get.close()
  }

  // -- isCorrectVersion: cached-binary version detection -----------------

  /** Build a tiny shell script that prints `body` on `-v` and exits 0,
    * mimicking what real mystem does for `mystem -v`. The script is
    * marked executable and self-deleting on JVM exit.
    */
  private def fakeBinaryPrinting(body: String): File = {
    val f = File.createTempFile("factory-fake-mystem-", ".sh")
    f.deleteOnExit()
    val script =
      s"""#!/bin/sh
         |# Fake mystem binary for FactoryTest.isCorrectVersion.
         |# Real mystem prints its version on -v; we mimic that
         |# behaviour irrespective of arguments to keep the test simple.
         |echo "${body.replace("\"", "\\\"")}"
         |""".stripMargin
    java.nio.file.Files.write(f.toPath, script.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    val _ = f.setExecutable(true)
    f
  }

  test("isCorrectVersion returns true when the binary's version output contains the requested version") {
    // Real mystem prints something like
    //   Yandex Mystem 3.1 (libmystem 3.1, build 2017-04-11)
    // We only need substring containment on the version we asked for.
    assume(new java.io.File("/bin/sh").canExecute, "needs /bin/sh")
    val factory = new Factory()
    val fake = fakeBinaryPrinting("Yandex Mystem 3.1 (linux64)")
    assert(factory.isCorrectVersion(fake, "3.1"))
  }

  test("isCorrectVersion returns false when the binary prints a DIFFERENT version") {
    // The trigger for the delete-and-refetch path: a stale binary from
    // an older or unrelated install. Pin: we DO refuse to reuse it.
    assume(new java.io.File("/bin/sh").canExecute, "needs /bin/sh")
    val factory = new Factory()
    val fake = fakeBinaryPrinting("Yandex Mystem 9.9.9 (custom)")
    assert(!factory.isCorrectVersion(fake, "3.1"))
  }

  test("isCorrectVersion returns false when the binary cannot be executed (broken cache file)") {
    // Cached file exists but isn't executable — common after a partial
    // download or a tarball that was extracted with the wrong umask.
    // Falling back to "version cannot be confirmed → re-fetch" is the
    // right move; the alternative is a confusing crash much later.
    val factory = new Factory()
    val notExec = File.createTempFile("factory-not-exec-", ".bin")
    notExec.deleteOnExit()
    java.nio.file.Files.write(notExec.toPath, "garbage".getBytes(java.nio.charset.StandardCharsets.UTF_8))
    // Deliberately do NOT call setExecutable(true).
    assert(!factory.isCorrectVersion(notExec, "3.1"))
  }

  test("isCorrectVersion returns false on a non-existent binary path") {
    val factory = new Factory()
    val ghost = new File("/tmp/nonexistent-mystem-binary-zzyzx-" + System.nanoTime())
    assert(!ghost.exists())
    assert(!factory.isCorrectVersion(ghost, "3.1"))
  }

  test("isCorrectVersion does substring matching on the version line") {
    // Real output isn't equal to the version string, it CONTAINS it.
    // Pin that we do plain `String.contains` and not exact equality —
    // "3.1" must match "Yandex Mystem 3.1 (...)".
    assume(new java.io.File("/bin/sh").canExecute, "needs /bin/sh")
    val factory = new Factory()
    val fake = fakeBinaryPrinting("I am Yandex Mystem version 3.1, hello!")
    assert(factory.isCorrectVersion(fake, "3.1"))
  }

  test("isCorrectVersion treats the version arg as plaintext, not a regex") {
    // If we ever swapped contains() for a regex match, "3.1" would
    // suddenly match "3X1" too because `.` is "any character" in regex.
    // Pin that swap as an intentional change rather than an accident.
    assume(new java.io.File("/bin/sh").canExecute, "needs /bin/sh")
    val factory = new Factory()
    val fake = fakeBinaryPrinting("Mystem 3X1")
    assert(!factory.isCorrectVersion(fake, "3.1"))
  }

  test("isCorrectVersion returns false when the binary exits non-zero") {
    // `(cmd).!!` throws on a non-zero exit code (it's the
    // "throw-on-failure" sibling of `.!`). Our Try/Failure branch
    // catches that and returns false — the binary is unusable, treat
    // it as a wrong-version cache to be replaced.
    assume(new java.io.File("/bin/sh").canExecute, "needs /bin/sh")
    val factory = new Factory()
    val f = File.createTempFile("factory-exit-1-", ".sh")
    f.deleteOnExit()
    java.nio.file.Files.write(f.toPath, "#!/bin/sh\nexit 1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))
    val _ = f.setExecutable(true)
    assert(!factory.isCorrectVersion(f, "3.1"))
  }
}
