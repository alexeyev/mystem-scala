package ru.stachek66.tools

import org.scalatest.funsuite.AnyFunSuite

import ru.stachek66.nlp.mystem.Properties

/** Behavioral spec for [[Decompressor.select]].
  *
  * Pinned contract: on Windows hosts (CurrentOs starts with "win"), `select`
  * returns the [[Zip]] decompressor; on every other host, [[TarGz]]. This
  * matches the asset shape mystem actually distributes — Windows builds are
  * `.zip`, everything else is `.tar.gz`.
  */
class DecompressorTest extends AnyFunSuite {

  test("Decompressor.select returns Zip on Windows hosts and TarGz elsewhere") {
    val expected =
      if (Properties.CurrentOs.contains("win"))
        "zip"
      else
        "tar.gz"
    assert(Decompressor.select.traditionalExtension === expected)
  }

  test("Decompressor.select is referentially stable: two calls return the same instance") {
    // Important because callers call `select` to retrieve the extension
    // for filename generation, then again to do the unpack. Returning a
    // fresh instance each time would still work but would be a smell.
    assert(Decompressor.select eq Decompressor.select)
  }
}
