name := "scala-sbt-client-usage"

version := "0.1"

scalaVersion := "2.12.10"

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "org.elasticsearch.elastiknn" %% "core" % "0.1.0-RC0"
)