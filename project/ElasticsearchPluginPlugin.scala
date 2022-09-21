import sbt.Keys._
import sbt.internal.graph.backend.SbtUpdateReport
import sbt.{Def, _}

object ElasticsearchPluginPlugin extends AutoPlugin {

  override val trigger = noTrigger

  override val requires = plugins.JvmPlugin

  object autoImport extends ElasticsearchPluginKeys

  import autoImport._

  override lazy val projectSettings: Seq[Def.Setting[_]] = List(
    libraryDependencies ++= Seq(
      "org.elasticsearch" % "elasticsearch" % elasticsearchVersion.value
    ),
    bundlePlugin := bundlePluginTask.value
  )

  private def bundlePluginTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sLog.value

    // Figure out how to get rid of elasticsearch and its transitive dependencies.

    val ignoredModules = for {
      c <- Classpaths.updateTask.value.configurations
      if c.configuration.name == "compile"
    } yield {
      println((c.configuration.name, c.modules.length, c.details.length))
      val moduleGraph = SbtUpdateReport.fromConfigurationReport(c, projectID.value)
      println(moduleGraph.nodes.length)
      println(moduleGraph.edges.length)
      moduleGraph.edges.foreach(println(_))
    }

//    // allDependencies
//    val jars = (Compile / dependencyClasspathAsJars)
//      .value
//      .filterNot {
//        af: Attributed[File] =>
//          af.data.getPath.contains("org/elasticsearch/elasticsearch") ||
//            af.data.getPath.contains("org/apache/lucene/lucene")
//      }
//    jars.sortBy(_.data.getName).foreach(j => println(j.data.getName))
//    println(jars.length)
    log.info(s"This is the bundlePlugin task: ${elasticsearchPluginVersion.value}, ${elasticsearchPluginDescription.value}, ${elasticsearchPluginName.value}")
  }
}
