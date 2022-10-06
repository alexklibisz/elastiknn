Global / scalaVersion := "2.13.9"

lazy val CirceVersion = "0.14.1"
lazy val CirceGenericExtrasVersion = "0.14.1"
lazy val ElasticsearchVersion = "8.4.3"
lazy val Elastic4sVersion = "8.0.0"
lazy val ElastiknnVersion = IO.read(file("version")).strip()
lazy val LuceneVersion = "9.3.0"

lazy val ScalacOptions = List("-Xfatal-warnings", "-Ywarn-unused:imports")

lazy val `elastiknn-root` = project
  .in(file("."))
  .settings(
    name := "elastiknn-root"
  )
  .aggregate(
    `elastiknn-api4s`,
    `elastiknn-client-elastic4s`,
    `elastiknn-lucene`,
    `elastiknn-models`,
    `elastiknn-plugin`,
    `elastiknn-testing`
  )

lazy val `elastiknn-api4s` = project
  .in(file("elastiknn-api4s"))
  .settings(
    name := "api4s",
    version := ElastiknnVersion,
    libraryDependencies ++= Seq(
      "org.elasticsearch" % "elasticsearch-x-content" % ElasticsearchVersion
    ),
    scalacOptions ++= ScalacOptions
  )

lazy val `elastiknn-client-elastic4s` = project
  .in(file("elastiknn-client-elastic4s"))
  .dependsOn(`elastiknn-api4s`)
  .settings(
    name := "client-elastic4s",
    version := ElastiknnVersion,
    libraryDependencies ++= Seq(
      "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % Elastic4sVersion
    ),
    scalacOptions ++= ScalacOptions
  )

lazy val `elastiknn-lucene` = project
  .in(file("elastiknn-lucene"))
  .dependsOn(`elastiknn-models`)
  .settings(
    name := "lucene",
    version := ElastiknnVersion,
    libraryDependencies ++= Seq(
      "org.apache.lucene" % "lucene-core" % LuceneVersion
    ),
    scalacOptions ++= ScalacOptions
  )

lazy val `elastiknn-models` = project
  .in(file("elastiknn-models"))
  .settings(
    name := "models",
    version := ElastiknnVersion,
    javacOptions ++= Seq(
      // Needed for @ForceInline annotation.
      "--add-exports",
      "java.base/jdk.internal.vm.annotation=ALL-UNNAMED"
    ),
    scalacOptions ++= ScalacOptions
  )

import ElasticsearchPluginPlugin.autoImport._

lazy val `elastiknn-plugin` = project
  .in(file("elastiknn-plugin"))
  .enablePlugins(ElasticsearchPluginPlugin)
  .dependsOn(
    `elastiknn-api4s`,
    `elastiknn-lucene`
  )
  .settings(
    name := "elastiknn",
    version := ElastiknnVersion,
    elasticsearchPluginName := "elastiknn",
    elasticsearchPluginClassname := "com.klibisz.elastiknn.ElastiknnPlugin",
    elasticsearchPluginDescription := "...",
    elasticsearchPluginVersion := ElastiknnVersion,
    elasticsearchVersion := ElasticsearchVersion,
    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "28.1-jre",
      "com.google.guava" % "failureaccess" % "1.0.1"
    ),
    scalacOptions ++= ScalacOptions
  )

lazy val `elastiknn-testing` = project
  .in(file("elastiknn-testing"))
  .dependsOn(`elastiknn-client-elastic4s`, `elastiknn-plugin`)
  .settings(
    Test / parallelExecution := false,
    Test / logBuffered := false,
    Test / testOptions += Tests.Argument("-oD"),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic-extras" % CirceGenericExtrasVersion,
      "io.circe" %% "circe-parser" % CirceVersion,
      "org.apache.lucene" % "lucene-core" % LuceneVersion,
      "org.elasticsearch" % "elasticsearch" % ElasticsearchVersion,
      "org.scalanlp" %% "breeze" % "1.3",
      "org.scalatest" %% "scalatest" % "3.2.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
      "com.klibisz.futil" %% "futil" % "0.1.2" % Test,
      "com.typesafe" % "config" % "1.4.0" % Test,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2" % Test,
      "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % Test,
      "org.apache.commons" % "commons-math3" % "3.6.1" % Test,
      "org.apache.lucene" % "lucene-analysis-common" % LuceneVersion % Test,
      "org.apache.lucene" % "lucene-backward-codecs" % LuceneVersion % Test,
      "org.elasticsearch" % "elasticsearch" % ElasticsearchVersion % Test,
      "org.pegdown" % "pegdown" % "1.4.2" % Test
    ),
    scalacOptions ++= ScalacOptions
  )
