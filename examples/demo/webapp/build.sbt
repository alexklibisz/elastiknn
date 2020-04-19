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

javaOptions in Universal ++= Seq(
  s"-Dpidfile.path=/tmp/play.pid",
  s"-Dconfig.file=/opt/docker/conf/application.conf",
)

//enablePlugins(JavaAppPackaging)

//enablePlugins(AssemblyPlugin)
//
//mainClass in assembly := Some("play.core.server.ProdServerStart")
//fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value)
//assemblyMergeStrategy in assembly := {
//  case manifest if manifest.contains("MANIFEST.MF") =>
//    // We don't need manifest files since sbt-assembly will create one with the given settings
//    MergeStrategy.discard
//  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") =>
//    // Keep the content for all reference-overrides.conf files
//    MergeStrategy.concat
//  case x =>
//    // For all the other files, use the default sbt-assembly merge strategy
//    val oldStrategy = (assemblyMergeStrategy in assembly).value
//    oldStrategy(x)
//}
//assemblyJarName in assembly := s"${name.value}.jar"
//test in assembly := {}

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"
