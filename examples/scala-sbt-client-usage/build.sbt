name := "scala-sbt-client-usage"

version := "0.1"

scalaVersion := "2.12.10"

resolvers += Resolver.mavenLocal

val pluginVersion = IO.readLines(new File("../../version")).head.strip()

libraryDependencies ++= Seq(
  "org.elasticsearch.elastiknn" %% "core" % pluginVersion,
  "org.elasticsearch.elastiknn" %% "client-elastic4s" % pluginVersion
)