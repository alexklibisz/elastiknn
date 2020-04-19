name := "demo-webapp"
organization := "com.klibisz.elastiknn"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.10"

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  guice,
  "com.klibisz.elastiknn" %% "client-elastic4s" % "0.1.0-PRE15",
  "com.sksamuel.elastic4s" %% "elastic4s-json-circe" % "7.6.0",
  "com.dripower" % "play-circe_2.12" % "2812.0",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
)

dockerBaseImage := "openjdk:11"
mainClass in Docker := Some("play.core.server.ProdServerStart")

// https://www.scala-sbt.org/sbt-native-packager/recipes/play.html#application-configuration
javaOptions in Universal ++= Seq(
  s"-Dpidfile.path=/tmp/play.pid",
  s"-Dconfig.file=/opt/docker/conf/application.conf",
)
