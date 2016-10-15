# A Scala wrapper for morphological analyzer Yandex.MyStem

## Introduction

Details about the algorithm can be found in [I. Segalovich «A fast morphological algorithm with unknown word guessing induced by a dictionary for a web search engine», MLMTA-2003, Las Vegas, Nevada, USA.](http://download.yandex.ru/company/iseg-las-vegas.pdf)

The wrapper's code in under MIT license, but please remember that Yandex.MyStem is not open source and licensed under [conditions of the Yandex License](https://legal.yandex.ru/mystem/).

## System Requirements

The wrapper should at least work on Ubuntu Linux 12.04+, and Windows 7+.

## Install

### Maven

[Maven central](http://search.maven.org/#artifactdetails|ru.stachek66.nlp|mystem-scala|0.1.4|jar)

```xml
<dependency>
  <groupId>ru.stachek66.nlp</groupId>
  <artifactId>mystem-scala</artifactId>
  <version>0.1.4</version>
</dependency>
```

## Issues

Only mystem 3.0 is supported currently.
Please [create issues for compatibility troubles and other requests.](https://github.com/alexeyev/mystem-scala/issues)

## Examples

Probably the most important thing to remember when working with mystem-scala is 
that you should have just one MyStem instance per mystem/mystem.exe file in your application.

###Scala 

```scala
import java.io.File

import ru.stachek66.nlp.mystem.holding.{Factory, MyStem, Request}

object MystemSingletonScala {

  val mystemAnalyzer: MyStem =
    new Factory("-igd --eng-gr --format json --weight")
      .newMyStem(
        "3.0",
        Option(new File("/home/coolguy/coolproject/3dparty/mystem"))).get()
}

object AppExampleScala extends App {

  MystemSingletonScala
    .mystemAnalyzer
    .analyze(Request("Есть большие пассажиры мандариновой травы"))
    .info
    .foreach(info => println(info.initial + " -> " + info.lex))
}
```

###Java 

```java
import ru.stachek66.nlp.mystem.holding.Factory;
import ru.stachek66.nlp.mystem.holding.MyStem;
import ru.stachek66.nlp.mystem.holding.MyStemApplicationException;
import ru.stachek66.nlp.mystem.holding.Request;
import ru.stachek66.nlp.mystem.model.Info;
import scala.Option;
import scala.collection.JavaConversions;

import java.io.File;

public class MyStemJavaExample {

    private final static MyStem mystemAnalyzer =
            new Factory("-igd --eng-gr --format json --weight")
                    .newMyStem("3.0", Option.<File>empty()).get();

    public static void main(final String[] args) throws MyStemApplicationException {

        final Iterable<Info> result =
                JavaConversions.asJavaIterable(
                        mystemAnalyzer
                                .analyze(Request.apply("И вырвал грешный мой язык"))
                                .info()
                                .toIterable());

        for (final Info info : result) {
            System.out.println(info.initial() + " -> " + info.lex() + " | " + info.rawResponse());
        }
    }
}
```
## Contacts

Anton Alekseev <anton.m.alexeyev@gmail.com>

## Thanks for reviews and contributions

* Vladislav Dolbilov, @darl
* Mikhail Malchevsky
* @anton-shirikov

## Also please see

* https://tech.yandex.ru/mystem/
* https://nlpub.ru/Mystem
* https://github.com/Digsolab/pymystem3
