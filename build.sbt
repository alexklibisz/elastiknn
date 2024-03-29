import ElasticsearchPluginPlugin.autoImport.*
import org.typelevel.scalacoptions.*

Global / scalaVersion := "3.3.3"

Global / scalacOptions += "-explain"

lazy val CirceVersion = "0.14.3"
lazy val ElasticsearchVersion = "8.12.2"
lazy val Elastic4sVersion = "8.11.5"
lazy val ElastiknnVersion = IO.read(file("version")).strip()
lazy val LuceneVersion = "9.9.1"

lazy val TestSettings = Seq(
  Test / parallelExecution := false,
  Test / logBuffered := false,
  Test / testOptions += Tests.Argument("-oD"),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
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
    `elastiknn-jmh-benchmarks`,
    `elastiknn-plugin`,
    `elastiknn-plugin-integration-tests`
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
    TestSettings
  )

lazy val `elastiknn-jmh-benchmarks` = project
  .in(file("elastiknn-jmh-benchmarks"))
  .dependsOn(`elastiknn-models` % "compile->compile;compile->test", `elastiknn-api4s`, `elastiknn-lucene`)
  .enablePlugins(JmhPlugin)
  .settings(
    Jmh / javaOptions ++= Seq("--add-modules", "jdk.incubator.vector"),
    libraryDependencies ++= Seq(
      "org.eclipse.collections" % "eclipse-collections" % "11.1.0",
      "org.eclipse.collections" % "eclipse-collections-api" % "11.1.0"
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
      "com.google.guava" % "guava" % "33.1.0-jre",
      "com.google.guava" % "failureaccess" % "1.0.2",
      "org.scalanlp" %% "breeze" % "2.1.0" % Test,
      "io.circe" %% "circe-parser" % CirceVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.5.3" % Test
    ),
    TestSettings
  )

lazy val `elastiknn-plugin-integration-tests` = project
  .in(file("elastiknn-plugin-integration-tests"))
  .dependsOn(`elastiknn-plugin` % "test->test")
  .settings(
    libraryDependencies += "io.circe" %% "circe-generic" % CirceVersion % Test,
    TestSettings
  )
