import sbt.Keys._
import sbt.internal.graph
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

    def buildDependencyMap(cr: ConfigurationReport): Map[ModuleID, List[ModuleID]] = {
      val moduleGraph = SbtUpdateReport.fromConfigurationReport(cr, projectID.value)
      val dependencyMap = moduleGraph.dependencyMap
      val emptyModules = moduleGraph.nodes.map(_.id).filterNot(dependencyMap.contains).map(g => g.organization % g.name % g.version -> List.empty).toMap
      emptyModules ++ dependencyMap
        .map {
          case (k,vv) => (k.organization % k.name % k.version) -> vv.toList.map(_.id).map(v => v.organization % v.name % v.version)
        }
      }

    def transitiveDependencyExists(dependencyMap: Map[ModuleID, List[ModuleID]])(from: ModuleID, to: ModuleID): Boolean =
        dependencyMap.get(from) match {
          case None => false
          case Some(lst) => lst.contains(to) || lst.exists(transitiveDependencyExists(dependencyMap)(_, to))
        }

    // We have to exclude Elasticsearch and any of its transitive dependencies in order to keep the zip file small.
    // To do this, traverse the ModuleGraph and build a list of any module that depends on the Elasticsearch module.
    val esMod = elasticsearchModule(elasticsearchVersion.value)
    val ignoredModules: Seq[ModuleID] = esMod +: (for {
      cr <- Classpaths.updateTask.value.configurations
      if cr.configuration.name == Configurations.Compile.name
      dm = buildDependencyMap(cr)
      mod <- dm.keys.toList.filter(transitiveDependencyExists(dm)(esMod, _))
    } yield mod)

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



//    log.info(s"This is the bundlePlugin task: ${elasticsearchPluginVersion.value}, ${elasticsearchPluginDescription.value}, ${elasticsearchPluginName.value}")
  }
}
