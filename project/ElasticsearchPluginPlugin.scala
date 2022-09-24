import sbt.Keys._
import sbt.internal.graph.backend.SbtUpdateReport
import sbt.{Def, _}

import java.io.{FileInputStream, FileOutputStream}
import java.util.zip.{ZipEntry, ZipOutputStream}

/**
  * SBT plugin providing functionality to run and build an Elasticsearch plugin.
  * See also https://github.com/elastic/elasticsearch/blob/7ad3cf0d34/build-tools/src/main/java/org/elasticsearch/gradle/plugin/PluginBuildPlugin.java
  */
object ElasticsearchPluginPlugin extends AutoPlugin {

  override val trigger = noTrigger

  override val requires = plugins.JvmPlugin

  object autoImport extends ElasticsearchPluginKeys

  import autoImport._

  override lazy val projectSettings: Seq[Def.Setting[_]] = List(
    libraryDependencies ++= Seq(
      "org.elasticsearch" % "elasticsearch" % elasticsearchVersion.value % Provided
    ),
    bundlePlugin := bundlePluginTaskImpl.value,
    run := runImpl.value
  )

  private def runImpl = Def.task {
    val log = sLog.value
    log.info("This is the run task")
  }

  private def bundlePluginTaskImpl: Def.Initialize[Task[Unit]] = Def.task {
    // Inspired by https://github.com/shikhar/eskka
    val log = sLog.value
    val pluginDescriptorFile: File = (Compile / target).value / "plugin-descriptor.properties"
    pluginDescriptorFile.deleteOnExit()
    IO.write(
      pluginDescriptorFile,
      s"""
         |# Elasticsearch plugin descriptor file
         |# This file must exist as 'plugin-descriptor.properties' inside a plugin.
         |version=${elasticsearchPluginVersion.value}
         |name=${elasticsearchPluginName.value}
         |description=${elasticsearchPluginDescription.value}
         |classname=${elasticsearchPluginClassname.value}
         |java.version=${System.getProperty("java.specification.version")}
         |elasticsearch.version=${elasticsearchVersion.value}
         |""".stripMargin.stripLeading
    )
    val pluginMetadataFiles = ((Compile / sourceDirectory).value / "plugin-metadata").listFiles()
    val pluginJar: File = (Compile / packageBin).value
    val internalDependencyJars = (Compile / internalDependencyAsJars).value.map(_.data)
    val runtimeDependencyJars = Classpaths.updateTask.value.select(configuration = configurationFilter("runtime"))
    val files = List(pluginDescriptorFile, pluginJar) ++ pluginMetadataFiles ++ runtimeDependencyJars ++ internalDependencyJars
    val elasticsearchLuceneJars = files.filter(f => f.getPath.contains("org/elasticsearch") || f.getPath.contains("org/apache/lucene"))
    if (elasticsearchLuceneJars.nonEmpty) {
      val msg = s"Found Elasticsearch and Lucene JARs on the runtime classpath." ++
        s" Elasticsearch and Lucene dependencies should use the % Provided dependency configuration."
      log.error(msg ++ s"\n[${elasticsearchLuceneJars.mkString(", ")}]")
      throw new IllegalStateException(msg)
    }
    val zipFile = (Compile / target).value / s"${elasticsearchPluginName.value}-${elasticsearchPluginVersion.value}.zip"
    IO.zip(files.map(f => (f -> f.getName)), zipFile, None)
    log.info(s"Generated plugin file ${zipFile.getPath} containing ${files.length + 1} files")
  }
}
