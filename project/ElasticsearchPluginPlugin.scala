import sbt.Keys._
import sbt.internal.graph.{GraphModuleId, ModuleGraph}
import sbt.internal.graph.backend.SbtUpdateReport
import sbt.{Def, _}

object ElasticsearchPluginPlugin extends AutoPlugin {

  override val trigger = noTrigger

  override val requires = plugins.JvmPlugin

  object autoImport extends ElasticsearchPluginKeys

  import autoImport._

  override lazy val projectSettings: Seq[Def.Setting[_]] = List(
    libraryDependencies ++= Seq(elasticsearchModule(elasticsearchVersion.value)),
    bundlePlugin := bundlePluginTask.value
  )

  private def elasticsearchModule(version: String): ModuleID = "org.elasticsearch" % "elasticsearch" % version

  private def bundlePluginTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sLog.value

    // We have to exclude Elasticsearch and any of its transitive dependencies in order to keep the zip file small.
    // To do this, traverse the ModuleGraph and build a list of any module that depends on the Elasticsearch module.
    // TODO: Figure out how to also ignore transitive dependencies of Elasticsearch.
    val esMod = elasticsearchModule(elasticsearchVersion.value)
    val ignoredModules: Seq[ModuleID] = esMod +: (for {
      cr <- Classpaths.updateTask.value.configurations
      if cr.configuration.name == Configurations.Compile.name
      mg = SbtUpdateReport.fromConfigurationReport(cr, projectID.value)
      (from, to) <- mg.edges
      if from.organization == esMod.organization && from.name == esMod.name && from.version == esMod.revision
    } yield to.organization % to.name % to.version)

    val jars = (Compile / dependencyClasspathAsJars)
      .value
      .filterNot {
        af: Attributed[File] =>
          val moduleIdOpt = af.metadata.entries.map(_.value).collectFirst {
            case m: ModuleID => m.organization % m.name % m.revision
          }
          val moduleId = moduleIdOpt.getOrElse(throw new IllegalStateException(s"JAR ${af.data.getName} missing required ModuleID metadata"))
          ignoredModules.contains(moduleId)
      }

    jars.map(_.data.getName).sorted.foreach(println(_))

//    log.info(s"This is the bundlePlugin task: ${elasticsearchPluginVersion.value}, ${elasticsearchPluginDescription.value}, ${elasticsearchPluginName.value}")
  }
}
