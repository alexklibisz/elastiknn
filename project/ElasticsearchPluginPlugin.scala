import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import sbt.Keys._
import sbt.internal.graph.backend.SbtUpdateReport
import sbt.{Def, _}

import java.nio.file.Files
import java.util.zip.GZIPInputStream
import scala.sys.process.Process

/** SBT plugin providing functionality to run and build an Elasticsearch plugin.
  * Also see:
  *  https://github.com/elastic/elasticsearch/blob/7ad3cf0d34/build-tools/src/main/java/org/elasticsearch/gradle/plugin/PluginBuildPlugin.java,
  *  build.sbt file in https://github.com/shikhar/eskka
  */
object ElasticsearchPluginPlugin extends AutoPlugin {

  override val trigger = noTrigger

  override val requires = plugins.JvmPlugin

  object autoImport extends ElasticsearchPluginKeys

  import autoImport._

  override lazy val projectSettings: Seq[Def.Setting[_]] = List(
    libraryDependencies ++= Seq(
      "org.elasticsearch" % "elasticsearch" % elasticsearchVersion.value
    ),
    cleanFiles ++= Seq(
      elasticsearchPluginDistributionDirectory.value,
      elasticsearchPluginBundleFile.value
    ),
    elasticsearchPluginDistributionDirectory := (Compile / target).value / s"elasticsearch-${elasticsearchVersion.value}",
    elasticsearchPluginBundle := elasticsearchPluginBundleImpl.value,
    elasticsearchPluginBundleFile := (Compile / target).value / s"${elasticsearchPluginName.value}-${elasticsearchPluginVersion.value}.zip",
    elasticsearchPluginRunSettings := Seq(
      "xpack.security.enabled=false",
      s"cluster.name=${elasticsearchPluginName.value}-sbt-cluster"
    ),
    elasticsearchPluginRun := elasticsearchPluginRunImpl.value,
    elasticsearchPluginDebugSettings := elasticsearchPluginRunSettings.value,
    elasticsearchPluginDebug := elasticsearchPluginDebugImpl.value,
    elasticsearchPluginDownloadDistribution := elasticsearchPluginDownloadDistributionImpl.value
  )

  private def elasticsearchModule(version: String): ModuleID = "org.elasticsearch" % "elasticsearch" % version

  private def elasticsearchPluginBundleImpl: Def.Initialize[Task[File]] = Def.task {
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

    // Build up the list of JARs that need to go in the zip file.
    // We need to remove Elasticsearch and all of its dependencies, which requires some gymnastics.
    // The official elasticsearch plugin does this by distinguishing runtime and compile dependencies.
    // I don't know of a way to do this in SBT without complicating other subprojects.
    val dependencyJars: Seq[File] = {
      def buildDependencyMap(cr: ConfigurationReport): Map[ModuleID, List[ModuleID]] = {
        val moduleGraph = SbtUpdateReport.fromConfigurationReport(cr, projectID.value)
        val dependencyMap = moduleGraph.dependencyMap
        val allModules = moduleGraph.nodes.map(n => n.id.organization % n.id.name % n.id.version -> List.empty).toMap
        allModules ++ dependencyMap
          .map { case (k, vv) =>
            (k.organization % k.name % k.version) ->
              vv.toList.map(v => v.id.organization % v.id.name % v.id.version)
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
        dependencyMap = buildDependencyMap(cr)
        mod <- dependencyMap.keys.toList.filter(transitiveDependencyExists(dependencyMap)(esMod, _))
      } yield mod)
      (Compile / dependencyClasspathAsJars).value
        .flatMap { af: Attributed[File] =>
          af.metadata.entries.map(_.value).collectFirst {
            case m: ModuleID if !ignoredModules.contains(m.organization % m.name % m.revision) => af.data
          }
        }
    }

    val files = List(pluginDescriptorFile, pluginJar) ++ pluginMetadataFiles ++ dependencyJars
    val zipFile = elasticsearchPluginBundleFile.value
    IO.zip(files.map(f => (f -> f.getName)), zipFile, None)
    log.info(s"Generated plugin file ${zipFile.getPath} containing ${files.length + 1} files.")
    zipFile
  }

  private def elasticsearchPluginRunImpl = Def.taskDyn {
    elasticsearchPluginRunGeneralImpl("", elasticsearchPluginRunSettings.value)
  }

  private def elasticsearchPluginDebugImpl = Def.taskDyn {
    elasticsearchPluginRunGeneralImpl(
      "-Xdebug -Xrunjdwp:transport=dt_socket,server=n,suspend=y,address=5005",
      elasticsearchPluginDebugSettings.value
    )
  }

  private def elasticsearchPluginDownloadDistributionImpl: Def.Initialize[Task[File]] = Def.task {
    val log = sLog.value
    val elasticsearchVersionSuffix = {
      val osName = System.getProperty("os.name").toLowerCase()
      val arch = System.getProperty("os.arch")
      if (osName.contains("mac")) s"darwin-$arch"
      else if (osName.contains("nix") || osName.contains("nux")) s"linux-$arch"
      else throw new RuntimeException(s"Unsupported operating system $osName, $arch")
    }
    val distributionFilename = s"elasticsearch-${elasticsearchVersion.value}-$elasticsearchVersionSuffix.tar.gz"
    val distributionUrl = new URL(s"https://artifacts.elastic.co/downloads/elasticsearch/$distributionFilename")
    val distributionDirectory = elasticsearchPluginDistributionDirectory.value
    val distributionParentDirectory = elasticsearchPluginDistributionDirectory.value.getParentFile
    if (!distributionDirectory.exists()) {
      log.info(s"Downloading Elasticsearch distribution from $distributionUrl to $distributionDirectory")
      val urlInputStream = distributionUrl.openStream()
      val gzipInputStream = new GZIPInputStream(urlInputStream)
      val tarInputStream = new TarArchiveInputStream(gzipInputStream)
      while (tarInputStream.getNextTarEntry != null) {
        val entry = tarInputStream.getCurrentEntry
        log.debug(s"Processing distribution entry ${entry.getName}")
        val entryFile = distributionParentDirectory / entry.getName
        if (entry.isDirectory && !entryFile.exists()) Files.createDirectories(entryFile.toPath)
        else if (entry.isFile) {
          Files.copy(tarInputStream, entryFile.toPath)
          if (entry.getMode == 493) entryFile.setExecutable(true, true)
        }
      }
      urlInputStream.close()
    } else {
      log.info(s"Found Elasticsearch distribution at $distributionParentDirectory. Skipping download.")
    }
    distributionDirectory
  }

  private def elasticsearchPluginRunGeneralImpl(esJavaOpts: String, esSettings: Seq[String]): Def.Initialize[Task[Unit]] = Def.task {
    val log = sLog.value
    val pluginBundle: File = elasticsearchPluginBundle.value
    val distributionDirectory: File = elasticsearchPluginDownloadDistribution.value

    log.info(s"Removing the plugin just in case it was already installed")
    val procUninstallPlugin = Process(s"./bin/elasticsearch-plugin remove ${elasticsearchPluginName.value}", distributionDirectory)
    procUninstallPlugin.! // Don't care if it fails.

    log.info("Installing the plugin")
    val procInstallPlugin = Process(s"./bin/elasticsearch-plugin install --verbose --batch ${pluginBundle.toURI}", distributionDirectory)
    procInstallPlugin.!! // Run in foreground and throw on non-zero exit.

    log.info(s"Run the elasticsearch cluster with the plugin installed")
    val settings = esSettings.map("-E" ++ _).mkString(" ")
    val procRunElasticsearch = Process(
      s"./bin/elasticsearch $settings",
      distributionDirectory,
      "ES_JAVA_OPTS" -> (s"$esJavaOpts " ++ sys.env.getOrElse("ES_JAVA_OPTS", ""))
    )
    procRunElasticsearch.! // Don't care if it fails.
  }
}
