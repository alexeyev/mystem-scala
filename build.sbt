val org = "ru.stachek66.nlp"
val project_name = "mystem-scala"
val project_version = "0.1.6"

lazy val mystem_scala = (project in file("."))
  .enablePlugins(GitVersioning)
  .settings(settings)
  .settings(
    name := project_name,
    libraryDependencies ++= Seq(
      library.json,
      library.logback,
      library.slf4j,
      library.compress,
      library.tsConfig,
      library.commons,
      library.scalaTest % Test
    )
  )

lazy val library =
  new {
    object Version {
      final val json      = "20170516"
      final val logback   = "1.2.3"
      final val slf4j     = "1.7.25"
      final val compress  = "1.14"
      final val tsConfig  = "1.3.1"
      final val commons   = "2.5"
      final val scalaTest = "3.0.3"
    }

    val json      = "org.json"           % "json"             % Version.json
    val logback   = "ch.qos.logback"     % "logback-classic"  % Version.logback
    val slf4j     = "org.slf4j"          % "slf4j-api"        % Version.slf4j
    val compress  = "org.apache.commons" % "commons-compress" % Version.compress
    val tsConfig  = "com.typesafe"       % "config"           % Version.tsConfig
    val commons   = "commons-io"         % "commons-io"       % Version.commons
    val scalaTest = "org.scalatest"      %% "scalatest"       % Version.scalaTest
  }

lazy val settings =
  commonSettings ++
  resolverSettings ++
  gitSettings ++
  publishSettings ++
  bintraySettings

lazy val commonSettings =
  Seq(
    organization := org,
    organizationName := "Anton Alekseev",
    version := project_version,
    scalaVersion := "2.12.2",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8"
    ),
    unmanagedSourceDirectories.in(Compile) := Seq(scalaSource.in(Compile).value),
    unmanagedSourceDirectories.in(Test) := Seq(scalaSource.in(Test).value)
  )

lazy val resolverSettings =
  Seq(
    resolvers ++= Seq(
      Resolver.bintrayRepo("alexeyev", "mystem-scala"),
      Resolver.jcenterRepo,
      Resolver.sonatypeRepo("snapshots")
    )
  )

lazy val gitSettings =
  Seq(
    git.useGitDescribe := true
  )


lazy val publishSettings =
  Seq(
    homepage := Some(url("https://github.com/alexeyev/mystem-scala")),
    scmInfo := Some(ScmInfo(url("https://github.com/alexeyev/mystem-scala"),
      "git@github.com:alexeyev/mystem-scala.git")),
    developers += Developer("alexeyev",
      "Anton Alekseev",
      "anton.m.alexeyev@gmail.com",
      url("http://www.stachek66.ru/")),
    pomIncludeRepository := (_ => false)
  )

lazy val bintraySettings =
  Seq(
    bintrayOrganization := Some("alexeyev"),
    bintrayRepository := "maven",
    bintrayPackage := "mystem-scala",
    bintrayPackageLabels := Seq("mystem", "scala")
  )
