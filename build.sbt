import ElasticsearchPluginPlugin.autoImport._

Global / scalaVersion := "2.13.9"

lazy val ElastiknnVersion = IO.read(file("version")).strip()

lazy val CirceVersion = "0.14.1"
lazy val CirceGenericExtrasVersion = "0.14.1"
lazy val Elastic4sVersion = "8.5.2"

// Versions for Elasticsearch 7.x
lazy val Elasticsearch7xVersion = "7.17.8"
lazy val Lucene8xVersion = "8.11.1"

// Versions for Elasticsearch 8.x
lazy val Elasticsearch8xVersion = "8.5.3"
lazy val Lucene9xVersion = "9.4.1"

lazy val ScalacOptions = List("-Xfatal-warnings", "-Ywarn-unused:imports")
lazy val TestSettings = Seq(
  Test / parallelExecution := false,
  Test / logBuffered := false,
  Test / testOptions += Tests.Argument("-oD"),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.0" % Test
)

lazy val `elastiknn-root` = project
  .in(file("."))
  .settings(
    name := "elastiknn-root"
  )
  .aggregate(
    `elastiknn-api4s`,
    `elastiknn-client-elastic4s`,
    `elastiknn-lucene`,
    `elastiknn-lucene-8x`,
    `elastiknn-models`,
    `elastiknn-plugin`
  )

lazy val `elastiknn-api4s` = project
  .in(file("elastiknn-api4s"))
  .settings(
    name := "api4s",
    version := ElastiknnVersion,
    libraryDependencies ++= Seq(
      "org.elasticsearch" % "elasticsearch-x-content" % Elasticsearch8xVersion,
      "io.circe" %% "circe-parser" % CirceVersion % Test,
    ),
    scalacOptions ++= ScalacOptions,
    TestSettings
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
    scalacOptions ++= ScalacOptions,
    TestSettings
  )

lazy val `elastiknn-lucene-8x` = project
  .in(file("elastikn-lucene-8x"))
  .dependsOn(`elastiknn-models`)
  .settings(
    name := "lucene",
    version := ElastiknnVersion,
    libraryDependencies ++= Seq(
      "org.apache.lucene" % "lucene-core" % Lucene8xVersion,
      "org.apache.lucene" % "lucene-analyzers-common" % Lucene8xVersion % Test
    )
  )

lazy val `elastiknn-lucene` = project
  .in(file("elastiknn-lucene"))
  .dependsOn(`elastiknn-models`)
  .settings(
    name := "lucene",
    version := ElastiknnVersion,
    libraryDependencies ++= Seq(
      "org.apache.lucene" % "lucene-core" % Lucene9xVersion,
      "org.apache.lucene" % "lucene-analysis-common" % Lucene9xVersion % Test
    ),
    scalacOptions ++= ScalacOptions,
    TestSettings
  )

lazy val `elastiknn-models` = project
  .in(file("elastiknn-models"))
  .dependsOn(`elastiknn-api4s` % "test->compile")
  .settings(
    name := "models",
    version := ElastiknnVersion,
    javacOptions ++= Seq(
      // Needed for @ForceInline annotation.
      "--add-exports",
      "java.base/jdk.internal.vm.annotation=ALL-UNNAMED"
    ),
    scalacOptions ++= ScalacOptions,
    TestSettings
  )

lazy val `elastiknn-plugin-common` = project
  .in(file("elastiknn-plugin-common"))
  .dependsOn(`elastiknn-api4s`)

lazy val `elastiknn-plugin` = project
  .in(file("elastiknn-plugin"))
  .enablePlugins(ElasticsearchPluginPlugin)
  .dependsOn(
    `elastiknn-api4s`,
    `elastiknn-lucene` % "compile->compile;test->test",
    `elastiknn-client-elastic4s` % "test->compile"
  )
  .configs(IntegrationTest.extend(Test))
  .settings(
    name := "elastiknn",
    version := ElastiknnVersion,
    elasticsearchPluginName := "elastiknn",
    elasticsearchPluginClassname := "com.klibisz.elastiknn.ElastiknnPlugin",
    elasticsearchPluginDescription := "...",
    elasticsearchPluginVersion := ElastiknnVersion,
    elasticsearchVersion := Elasticsearch8xVersion,
    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "28.1-jre",
      "com.google.guava" % "failureaccess" % "1.0.1",
      "org.scalanlp" %% "breeze" % "1.3" % Test,
      "io.circe" %% "circe-parser" % CirceVersion % Test,
      "io.circe" %% "circe-generic-extras" % CirceGenericExtrasVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
      "com.klibisz.futil" %% "futil" % "0.1.2" % Test
    ),
    scalacOptions ++= ScalacOptions,
    Defaults.itSettings,
    TestSettings
  )
