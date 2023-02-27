import ElasticsearchPluginPlugin.autoImport._

Global / scalaVersion := "2.13.10"

lazy val CirceVersion = "0.14.3"
lazy val ElasticsearchVersion = "8.6.2"
lazy val Elastic4sVersion = "8.5.3"
lazy val ElastiknnVersion = IO.read(file("version")).strip()
lazy val LuceneVersion = "9.4.2"

lazy val ScalacOptions = List("-Xfatal-warnings", "-Ywarn-unused:imports")
lazy val TestSettings = Seq(
  Test / parallelExecution := false,
  Test / logBuffered := false,
  Test / testOptions += Tests.Argument("-oD"),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.15" % Test
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
    `elastiknn-models`,
    `elastiknn-micro-benchmarks`,
    `elastiknn-plugin`
  )

lazy val `elastiknn-api4s` = project
  .in(file("elastiknn-api4s"))
  .settings(
    name := "api4s",
    version := ElastiknnVersion,
    libraryDependencies ++= Seq(
      "org.elasticsearch" % "elasticsearch-x-content" % ElasticsearchVersion,
      "io.circe" %% "circe-parser" % CirceVersion % Test
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

lazy val `elastiknn-lucene` = project
  .in(file("elastiknn-lucene"))
  .dependsOn(`elastiknn-models`)
  .settings(
    name := "lucene",
    version := ElastiknnVersion,
    libraryDependencies ++= Seq(
      "org.apache.lucene" % "lucene-core" % LuceneVersion,
      "org.apache.lucene" % "lucene-analysis-common" % LuceneVersion % Test
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
      "--add-modules",
      "jdk.incubator.vector",
      "--add-exports",
      "java.base/jdk.internal.vm.vector=ALL-UNNAMED",
      "--add-exports",
      "java.base/jdk.internal.vm.annotation=ALL-UNNAMED"
    ),
    scalacOptions ++= ScalacOptions,
    TestSettings
  )

lazy val `elastiknn-micro-benchmarks` = project
  .in(file("elastiknn-micro-benchmarks"))
  .dependsOn(`elastiknn-models`, `elastiknn-api4s`)
  .enablePlugins(JmhPlugin)
  .settings(
    Jmh / javaOptions ++= Seq("--add-modules", "jdk.incubator.vector"),
    resolvers += "jitpack" at "https://jitpack.io",
    libraryDependencies ++= Seq(
      "org.eclipse.collections" % "eclipse-collections" % "11.1.0",
      "org.eclipse.collections" % "eclipse-collections-api" % "11.1.0",
      "com.koloboke" % "koloboke-impl-common-jdk8" % "1.0.0",
      "org.agrona" % "agrona" % "1.17.1",
      "com.github.Speiger" % "Primitive-Collections" % "0.8.0"
    )
  )

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
    elasticsearchVersion := ElasticsearchVersion,
    elasticsearchPluginRunSettings += "elastiknn.jdk-incubator-vector.enabled=true",
    elasticsearchPluginEsJavaOpts += "--add-modules jdk.incubator.vector",
    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "28.1-jre",
      "com.google.guava" % "failureaccess" % "1.0.1",
      "org.scalanlp" %% "breeze" % "1.3" % Test,
      "io.circe" %% "circe-parser" % CirceVersion % Test,
      "io.circe" %% "circe-generic-extras" % CirceVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.2.11" % Test,
      "com.klibisz.futil" %% "futil" % "0.1.2" % Test
    ),
    scalacOptions ++= ScalacOptions,
    Defaults.itSettings,
    TestSettings
  )
