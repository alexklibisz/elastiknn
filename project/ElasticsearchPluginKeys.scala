import sbt._

trait ElasticsearchPluginKeys {
  // Settings.
  lazy val elasticsearchPluginBundleFile =
    settingKey[File]("The Elasticsearch plugin zip file produced via `sbt elasticsearchBundlePlugin`.")
  lazy val elasticsearchPluginClassname =
    settingKey[String]("The name of the class to load, fully-qualified.")
  lazy val elasticsearchPluginDescription =
    settingKey[String]("The plugin description.")
  lazy val elasticsearchPluginName =
    settingKey[String]("The plugin name.")
  lazy val elasticsearchPluginVersion =
    settingKey[String]("The plugin version.")
  lazy val elasticsearchPluginDistributionDirectory =
    settingKey[File]("The directory where the Elasticsearch distribution is downloaded.")
  lazy val elasticsearchPluginRunSettings =
    settingKey[Seq[String]]("Settings used when running an Elasticsearch node via `sbt elasticsearchPluginRun`.")
  lazy val elasticsearchPluginDebugSettings =
    settingKey[Seq[String]]("Settings used when running an Elasticsearch node via `sbt elasticsearchPluginDebug`.")
  lazy val elasticsearchVersion = settingKey[String]("The Elasticsearch version for this plugin.")
  // Tasks.
  lazy val elasticsearchPluginBundle =
    taskKey[File]("Bundle the plugin into a zip file that can be installed via `elasticsearch-plugin install`.")
  lazy val elasticsearchPluginDebug =
    taskKey[Unit]("Run a local Elasticsearch node with the plugin installed in debug mode to set and inspect breakpoints.")
  lazy val elasticsearchPluginDownloadDistribution =
    taskKey[File]("Download the Elasticsearch distribution from https://www.elastic.co/downloads/elasticsearch.")
  lazy val elasticsearchPluginRun =
    taskKey[Unit]("Run a local Elasticsearch node with the plugin installed.")
}
