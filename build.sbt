import Dependencies._

val org = "ru.stachek66.nlp"
val project_name = "mystem-scala"
val project_version = "0.1.5"

name := project_name

lazy val mystem_scala = (project in file("."))
  .settings(settings)
  .settings(
    libraryDependencies ++= allDeps
  )
  .enablePlugins(GitVersioning)

lazy val settings =
  commonSettings ++
  gitSettings ++
  publishSettings ++
  bintraySettings

lazy val commonSettings =
  Seq(
    organization := org,
    organizationName := "NSA Ltd.",
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

lazy val gitSettings =
  Seq(
    git.useGitDescribe := true
  )


lazy val publishSettings =
  Seq(
    homepage := Some(url("https://github.com/cnsa/mystem-scala")),
    scmInfo := Some(ScmInfo(url("https://github.com/cnsa/mystem-scala"),
      "git@github.com:cnsa/mystem-scala.git")),
    developers += Developer("merqlove",
      "Alexander Merkulov",
      "sasha@merqlove.ru",
      url("https://github.com/merqlove")),
    pomIncludeRepository := (_ => false)
  )

lazy val bintraySettings =
  Seq(
    bintrayOrganization := Some("cnsa"),
    bintrayRepository := "maven",
    bintrayPackage := "mystem-scala",
    bintrayPackageLabels := Seq("mystem", "scala")
  )
