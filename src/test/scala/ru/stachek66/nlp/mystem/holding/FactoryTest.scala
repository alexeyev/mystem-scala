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
}
