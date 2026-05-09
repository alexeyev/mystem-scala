# A Scala / JVM wrapper for morphological analyzer Yandex.MyStem

[![CI](https://github.com/alexeyev/mystem-scala/actions/workflows/ci.yml/badge.svg)](https://github.com/alexeyev/mystem-scala/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Introduction

Details about the algorithm can be found in
[I. Segalovich «A fast morphological algorithm with unknown word guessing induced by a dictionary for a web search engine», MLMTA-2003](http://download.yandex.ru/company/iseg-las-vegas.pdf).

The wrapper itself is MIT-licensed, but please remember that
**Yandex.MyStem is not open source** and is licensed under the
[Yandex MyStem licensing terms](https://legal.yandex.ru/mystem/).

## System requirements

- Java 8 or later (the artifacts are compiled with `target=1.8`).
- Linux, macOS, or Windows. The wrapper downloads the appropriate
  `mystem` binary the first time it is needed; you can also pass a
  pre-installed binary's path explicitly.

## Install

The artifact is cross-built for **Scala 2.12** and **Scala 2.13**.

### Maven

TODO: deploy recent improvements


```xml
<!-- For Scala 2.13 -->
<dependency>
    <groupId>ru.stachek66.nlp</groupId>
    <artifactId>mystem-scala_2.13</artifactId>
    <version>0.3.1</version>
</dependency>

<!-- For Scala 2.12 -->
<dependency>
    <groupId>ru.stachek66.nlp</groupId>
    <artifactId>mystem-scala_2.12</artifactId>
    <version>0.3.1</version>
</dependency>
```

### sbt

```scala
libraryDependencies += "ru.stachek66.nlp" %% "mystem-scala" % "0.3.0"
```

## Versioning notes

- `mystem` versions 3.0 and 3.1 are supported.
- `0.3.0` modernizes the build (Scala 2.12 / 2.13 cross-build, Java 8 target,
  publishing via the Sonatype Central Portal) and fixes the long-standing
  process-leak issue (#3). See [CHANGELOG](#changelog) below.
- Please [open issues](https://github.com/alexeyev/mystem-scala/issues) for
  compatibility troubles or other requests.

## Examples

> Important: keep **one `MyStem` instance per `mystem` binary** in your
> application. The instance owns a long-lived child process; constructing
> and discarding many instances will spawn many processes.

### Scala

```scala
import java.io.File
import ru.stachek66.nlp.mystem.holding.{Factory, MyStem, Request}

object Example {

  // Construct once, reuse, close on shutdown.
  val mystem: MyStem =
    new Factory("-igd --eng-gr --format json --weight")
      .newMyStem("3.0", Option(new File("/path/to/mystem"))) // or None to auto-download
      .get

  def main(args: Array[String]): Unit = try {
    mystem
      .analyze(Request("Есть большие пассажиры мандариновой травы"))
      .info
      .foreach(info => println(info.initial + " -> " + info.lex))
  } finally mystem.close() // returns the OS process. Idempotent.
}
```

`MyStem` extends `AutoCloseable`, so you can also use it from any
`Using` / try-with-resources style helper:

```scala
import scala.util.Using
Using.resource(new Factory().newMyStem("3.0").get) { mystem =>
  mystem.analyze(Request("Привет, мир!")).info.foreach(println)
}
```

A JVM shutdown hook is also installed automatically as a safety net for
code paths that forget to call `close()`.

### Java (Java 8+)

`scala.collection.JavaConversions` was removed in Scala 2.13. The wrapper
now exposes a Java-friendly accessor on `Response`, so Java callers do not
need any Scala collection conversions:

```java
import ru.stachek66.nlp.mystem.holding.Factory;
import ru.stachek66.nlp.mystem.holding.MyStem;
import ru.stachek66.nlp.mystem.holding.MyStemApplicationException;
import ru.stachek66.nlp.mystem.holding.Request;
import ru.stachek66.nlp.mystem.model.Info;
import scala.Option;

import java.io.File;
import java.util.List;

public final class MyStemJavaExample {

    private static final MyStem MYSTEM =
            new Factory("-igd --eng-gr --format json --weight")
                    .newMyStem("3.0", Option.<File>empty())
                    .get();

    public static void main(String[] args) throws MyStemApplicationException {
        try {
            List<Info> result =
                    MYSTEM.analyze(Request.apply("И вырвал грешный мой язык")).getInfoAsList();

            for (Info info : result) {
                System.out.println(info.initial() + " -> " + info.lex() + " | " + info.rawResponse());
            }
        } finally {
            MYSTEM.close();
        }
    }
}
```

`MYSTEM.close()` is also safe to call as the body of a `try-with-resources`
since `MyStem` implements `java.lang.AutoCloseable`.

## Changelog

### 0.3.1

- **`GrammarInfo.person` (API change).** The `1p` / `2p` / `3p` person
  tags emitted by mystem are now populated into a new `person:
  Set[Person.Value]` field. Previously the parser silently dropped them
  on the floor. Note: positional constructor calls to `GrammarInfo` will
  break — named-argument call sites are unaffected.
- **Parens-pipe `gr` parsing.** Real mystem 3.x output with `--weight`
  emits multi-analysis strings like
  `A,plen=(acc,sg,m,anim|gen,sg,m|gen,sg,n)`. The pre-existing parser
  threw `NoSuchElementException` on the leading `(`. New
  `GrammarInfoParsing.toGrammarInfos(s): List[GrammarInfo]` returns one
  `GrammarInfo` per pipe-alternative; the previous `toGrammarInfo`
  remains and now returns the most-likely interpretation (mystem orders
  alternatives by descending probability, so `.head`).
- **Wire-format aliases.** mystem 3.x emits `indic` (we declared
  `Value("ind")` for indicative mood) and `praet` (we declared
  `Value("past")` for past tense). Without alias support every real
  verb output threw on parsing. `GrammarMapBuilder.aliases` (public)
  now maps `indic`→`ind` and `praet`→`past`; both forms parse.
- **Process robustness.** `ExternalProcessServer.syncRequest` no
  longer spins forever in `while (!reader.ready()) {}` when the child
  process exits without responding — `BufferedReader.ready()` returns
  `false` at EOF, not true. The busy-wait now also gates on
  `process.isAlive`, the drain loop breaks on `null` (avoiding
  appending the literal string `"null"`), and an exit-without-output
  surfaces as `IOException("process exited before producing any
  response")` so `FailSafeExternalProcessServer` can spawn a fresh
  child rather than wrap an empty success. The restart-on-death path
  is now functionally testable.
- **Refactor:** `Factory.getExecutable`'s cached-binary version check
  is extracted into `private[holding] isCorrectVersion(file, version):
  Boolean`. Same observable contract, but the version-matching logic
  is now exercisable in unit tests without hitting the CDN.
- **Test coverage.** ~70 new tests (75 → 140 across 15 test classes).
  Statement coverage > 80%, branch coverage > 74% under scoverage.
  Direct unit tests for `syncRequest` (using `python3 -u` as a portable
  line-buffered echo stand-in), `isCorrectVersion`, `MyStem.normalize`,
  `MyStemApplicationException`, archive edge cases (empty archive,
  directory-as-first-entry), `FailSafeExternalProcessServer`
  restart-on-death, plus broader coverage of `GrammarInfoParsing`
  (parens-pipe alternatives, person, alias support, fail-loud on
  unknown tags inside parens).

### 0.3.0

- **Fixed process leak (#3):** `MyStem` now extends `AutoCloseable`; an
  internal JVM shutdown hook destroys the spawned `mystem` process if the
  user forgets to call `close()`. This unblocks Maven Surefire / `mvn
  package` runs that previously hung on Windows.
- **Java-friendly API:** `Response#getInfoAsList(): java.util.List<Info>`
  replaces the need for the (removed) `scala.collection.JavaConversions`.
- **Cross-built for Scala 2.12 + 2.13** with profiles `scala-2.12` /
  `scala-2.13`.
- **Java 8 target** preserved; runs on JDK 8/11/17 (CI matrix).
- **Modernized build:** publishing via Sonatype Central Portal
  (OSSRH was sunset 2025-06-30); current `commons-compress`, `slf4j`,
  `org.json`, `scalatest`, `scala-maven-plugin`.
- **`logback-classic` is now a `test` dependency**; consumers pick their
  own logging backend (only `slf4j-api` is on the compile classpath).
- **OS detection fixed** for modern Windows (10/11) — used to misroute to
  the macOS download URL.
- **`Traversable` → `Iterable`** throughout (deprecated/removed in 2.13/3).
- **Linting + formatting:** scalafmt, strict scalac flags
  (`-deprecation -feature -unchecked -Xlint:_`).
- **GitHub Actions CI** with a Java × Scala matrix and scoverage report.

### Older versions

See `git log` and the
[release page](https://github.com/alexeyev/mystem-scala/releases).

## How to cite

```bibtex
@misc{alekseev2018mystemscala,
    author = {Anton Alekseev},
    title = {mystem-scala},
    year = {2018},
    publisher = {GitHub},
    journal = {GitHub repository},
    howpublished = {\url{https://github.com/alexeyev/mystem-scala/}},
    commit = {the latest commit of the codebase you have used}
}
```

If you cite the wrapper, please also cite
[the original algorithm's paper](http://download.yandex.ru/company/iseg-las-vegas.pdf).

## Contacts

Anton Alekseev <anton.m.alexeyev@gmail.com>

## Thanks for reviews, reports and contributions

- Vladislav Dolbilov, @darl
- Mikhail Malchevsky
- @anton-shirikov
- Filipp Malkovsky
- @dizzy7

## See also

- <https://yandex.ru/dev/mystem/>
- <https://nlpub.ru/Mystem>
- <https://github.com/Digsolab/pymystem3>
