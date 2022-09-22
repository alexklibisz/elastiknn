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
    libraryDependencies ++= Seq(elasticsearchModule(elasticsearchVersion.value)),
    bundlePlugin := bundlePluginTaskImpl.value
  )

  private def elasticsearchModule(version: String): ModuleID = "org.elasticsearch" % "elasticsearch" % version

  private def bundlePluginTaskImpl: Def.Initialize[Task[Unit]] = Def.task {
    val log = sLog.value

    // Generate plugin properties.
    val properties =
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

    // Build up the list of JARs that need to go in the zip file.
    // We need to remove Elasticsearch and all of its dependencies, which requires some careful munging.
    // The official elasticsearch plugin does this by distinguishing runtime and compile dependencies.
    // I don't know of a way to do this in SBT.
    val jars: Seq[sbt.File] = {
      def buildDependencyMap(cr: ConfigurationReport): Map[ModuleID, List[ModuleID]] = {
        val moduleGraph = SbtUpdateReport.fromConfigurationReport(cr, projectID.value)
        val dependencyMap = moduleGraph.dependencyMap
        val emptyModules =
          moduleGraph.nodes.map(_.id).filterNot(dependencyMap.contains).map(g => g.organization % g.name % g.version -> List.empty).toMap
        emptyModules ++ dependencyMap
          .map {
            case (k, vv) => (k.organization % k.name % k.version) -> vv.toList.map(_.id).map(v => v.organization % v.name % v.version)
          }
      }

      def transitiveDependencyExists(dependencyMap: Map[ModuleID, List[ModuleID]])(from: ModuleID, to: ModuleID): Boolean =
        dependencyMap.get(from) match {
          case None      => false
          case Some(lst) => lst.contains(to) || lst.exists(transitiveDependencyExists(dependencyMap)(_, to))
        }

      val esMod = elasticsearchModule(elasticsearchVersion.value)
      val ignoredModules: Seq[ModuleID] = esMod +: (for {
        cr <- Classpaths.updateTask.value.configurations
        if cr.configuration.name == Configurations.Compile.name
        dm = buildDependencyMap(cr)
        mod <- dm.keys.toList.filter(transitiveDependencyExists(dm)(esMod, _))
      } yield mod)
      val packageJar: File = (Compile / packageBin).value
      val dependencyJars: Seq[File] = (Compile / dependencyClasspathAsJars).value
        .filterNot { af: Attributed[File] =>
          val moduleIdOpt = af.metadata.entries.map(_.value).collectFirst {
            case m: ModuleID => m.organization % m.name % m.revision
          }
          val moduleId =
            moduleIdOpt.getOrElse(throw new IllegalStateException(s"JAR ${af.data.getName} missing required ModuleID metadata"))
          ignoredModules.contains(moduleId)
        }
        .map(_.data)
      packageJar +: dependencyJars
    }

    val pluginMetadataDirectory = (Compile / sourceDirectory).value / "plugin-metadata"
    val allFilesOnDisk: Seq[sbt.File] = (jars ++ pluginMetadataDirectory.listFiles)

    // Build the zip file.
    val targetDirectory = (Compile / target).value
    val zipFile = targetDirectory / s"${elasticsearchPluginName.value}-${elasticsearchPluginVersion.value}.zip"
    val zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))

    // Add files from disk.
    allFilesOnDisk.foreach { f =>
      val zipEntry = new ZipEntry(f.getName)
      val fileInputStream = new FileInputStream(f)
      zipOutputStream.putNextEntry(zipEntry)
      zipOutputStream.write(fileInputStream.readAllBytes())
      fileInputStream.close()
    }

    // Add plugin properties.
    val zipEntry = new ZipEntry("plugin-descriptor.properties")
    zipOutputStream.putNextEntry(zipEntry)
    zipOutputStream.write(properties.getBytes)

    // Close the stream to write.
    zipOutputStream.close()

    log.info(s"Generated plugin file ${zipFile.getPath} containing ${allFilesOnDisk.length + 1} files")
  }
}
