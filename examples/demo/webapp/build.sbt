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

dockerBaseImage := "openjdk:12"
dockerExposedPorts ++= Seq(8097, 9000)
mainClass in Docker := Some("play.core.server.ProdServerStart")

javaOptions in Universal ++= Seq(
  // https://www.scala-sbt.org/sbt-native-packager/recipes/play.html#application-configuration
  s"-Dpidfile.path=/tmp/play.pid",
  s"-Dconfig.file=/opt/docker/conf/application.conf",

  // Enable VisualVM attachment.
  "-Dcom.sun.management.jmxremote.ssl=false",
  "-Dcom.sun.management.jmxremote.authenticate=false",
  "-Dcom.sun.management.jmxremote.local.only=false",
  "-Dcom.sun.management.jmxremote.port=8097",
  "-Dcom.sun.management.jmxremote.rmi.port=8097",
  "-Djava.rmi.server.hostname=localhost"
)
