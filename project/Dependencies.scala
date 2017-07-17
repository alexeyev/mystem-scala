import sbt.Keys._
import sbt._

/*
 * Copyright 2017 NSA Ltd. and other contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

object Dependencies {
  resolvers ++= Seq(
    Resolver.bintrayRepo("cnsa", "mystem-scala"),
    Resolver.jcenterRepo
  )

  lazy val jsonVersion      = "20170516"
  lazy val logbackVersion   = "1.2.3"
  lazy val slf4jVersion     = "1.7.25"
  lazy val compressVersion  = "1.14"
  lazy val tsConfigVersion  = "1.3.1"
  lazy val commonsVersion   = "2.5"
  lazy val scalaTestVersion = "3.0.3"

  val allDeps =
    Seq(
      "org.json"           % "json"             % jsonVersion,
      "ch.qos.logback"     % "logback-classic"  % logbackVersion,
      "org.slf4j"          % "slf4j-api"        % slf4jVersion,
      "org.apache.commons" % "commons-compress" % compressVersion,
      "com.typesafe"       % "config"           % tsConfigVersion,
      "commons-io"         % "commons-io"       % commonsVersion,
      "org.scalatest"      %% "scalatest"       % scalaTestVersion % "test"
    )
}