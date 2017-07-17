logLevel := Level.Warn

// The Typesafe repository
resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.typesafeRepo("releases")
)

//addSbtPlugin("com.dwijnand"      % "sbt-travisci"  % "1.1.0")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"       % "1.0.0")
//addSbtPlugin("com.typesafe.sbt"  % "sbt-multi-jvm" % "0.3.11")
addSbtPlugin("com.typesafe.sbt"  % "sbt-git"       % "0.9.3")
addSbtPlugin("org.foundweekends" % "sbt-bintray"   % "0.5.0")
