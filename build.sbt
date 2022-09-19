
Global / scalaVersion := "2.13.6"

lazy val CirceVersion = "0.14.1"
lazy val CirceGenericExtrasVersion = "0.14.1"
lazy val ElasticsearchVersion = "8.2.1"
lazy val Elastic4sVersion = "8.0.0"
lazy val ElastiknnVersion = IO.read(file("version")).strip()
lazy val LuceneVersion = "9.1.0"

lazy val elastiknn = project
  .in(file("."))
  .settings(
    name := "elastiknn"
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
    libraryDependencies ++= Seq(
      "org.elasticsearch" % "elasticsearch-x-content" % ElasticsearchVersion
    )
  )

lazy val `elastiknn-client-elastic4s` = project
  .in(file("elastiknn-client-elastic4s"))
  .dependsOn(`elastiknn-api4s`)
  .settings(
    libraryDependencies ++= Seq(
      "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % Elastic4sVersion
    )
  )

lazy val `elastiknn-lucene` = project
  .in(file("elastiknn-lucene"))
  .dependsOn(`elastiknn-models`)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.lucene" % "lucene-core" % LuceneVersion
    )
  )

lazy val `elastiknn-models` = project
  .in(file("elastiknn-models"))
  .settings(
    javacOptions ++= Seq(
      // Needed for @ForceInline annotation.
      "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED"
    )
  )

lazy val `elastiknn-plugin` = project
  .in(file("elastiknn-plugin"))
  .dependsOn(`elastiknn-api4s`, `elastiknn-lucene`)
  .settings(
    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "28.1-jre",
      "com.google.guava" % "failureaccess" % "1.0.1",
      "org.apache.lucene" % "lucene-backward-codecs" % LuceneVersion,
      "org.elasticsearch" % "elasticsearch" % ElasticsearchVersion
    )
  )

lazy val `elastiknn-testing` = project
  .in(file("elastiknn-testing"))
  .dependsOn(`elastiknn-client-elastic4s`, `elastiknn-plugin`)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic-extras" % CirceGenericExtrasVersion,
      "io.circe" %% "circe-parser" % CirceVersion,
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
    )
  )
