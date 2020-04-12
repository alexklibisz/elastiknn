name := "demo-webapp"
organization := "com.klibisz.elastiknn"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.10"

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  guice,
  "com.klibisz.elastiknn" %% "client-elastic4s" % "0.1.0-PRE15",
  "com.dripower" % "play-circe_2.12" % "2812.0",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"
