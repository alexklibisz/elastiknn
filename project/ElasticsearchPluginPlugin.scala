import sbt.Keys._
import sbt.internal.graph.backend.SbtUpdateReport
import sbt.{Def, _}

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
      "org.elasticsearch" % "elasticsearch" % elasticsearchVersion.value
    ),
    bundlePlugin := bundlePluginTaskImpl.value,
    run := runImpl.value
  )

  private def runImpl = Def.task {
    val log = sLog.value
    log.info("This is the run task")
  }

  private def elasticsearchModule(version: String): ModuleID = "org.elasticsearch" % "elasticsearch" % version

  private def bundlePluginTaskImpl: Def.Initialize[Task[Unit]] = Def.task {
    // Partially inspired by https://github.com/shikhar/eskka
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
          .map {
            case (k, vv) =>
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
    val zipFile = (Compile / target).value / s"${elasticsearchPluginName.value}-${elasticsearchPluginVersion.value}.zip"
    IO.zip(files.map(f => (f -> f.getName)), zipFile, None)
    log.info(s"Generated plugin file ${zipFile.getPath} containing ${files.length + 1} files")
  }
}
