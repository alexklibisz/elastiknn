import sbt._

trait ElasticsearchPluginKeys {

  lazy val elasticsearchVersion = settingKey[String]("The Elasticsearch version for this plugin.")

  lazy val elasticsearchPluginVersion = settingKey[String]("The plugin version.")

  lazy val elasticsearchPluginName = settingKey[String]("The plugin name.")

  lazy val elasticsearchPluginDescription = settingKey[String]("The plugin description.")

  lazy val elasticsearchPluginClassname = settingKey[String]("The name of the class to load, fully-qualified.")

  lazy val bundlePlugin = taskKey[Unit]("Bundle the plugin to a zip file that can be installed in an Elasticsearch node.")

}
