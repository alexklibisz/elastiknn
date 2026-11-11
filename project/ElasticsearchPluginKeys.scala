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
  lazy val elasticsearchPluginEsJavaOpts =
    settingKey[Seq[String]]("Java settings passed via ES_JAVA_OPTS environment variable.")
  lazy val elasticsearchPluginRunSettings =
    settingKey[Seq[String]]("Settings used when running an Elasticsearch node via `sbt elasticsearchPluginRun`.")
  lazy val elasticsearchVersion = settingKey[String]("The Elasticsearch version for this plugin.")
  // Tasks. These are @transient because they have side effects and should not be cached by sbt 2.
  @transient
  lazy val elasticsearchPluginBundle =
    taskKey[File]("Bundle the plugin into a zip file that can be installed via `elasticsearch-plugin install`.")
  @transient
  lazy val elasticsearchPluginDebug =
    taskKey[Unit]("Run a local Elasticsearch node with the plugin installed in debug mode to set and inspect breakpoints.")
  @transient
  lazy val elasticsearchPluginDownloadDistribution =
    taskKey[File]("Download the Elasticsearch distribution from https://www.elastic.co/downloads/elasticsearch.")
  @transient
  lazy val elasticsearchPluginRun =
    taskKey[Unit]("Run a local Elasticsearch node with the plugin installed.")
}
