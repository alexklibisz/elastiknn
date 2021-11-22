name := "scala-sbt-client-usage"

version := "0.1"

scalaVersion := "2.13.6"

resolvers += Resolver.mavenLocal

val pluginVersion = IO.readLines(new File("../../version")).head.strip()

libraryDependencies ++= Seq(
  "com.klibisz.elastiknn" %% "client-elastic4s" % pluginVersion
)
