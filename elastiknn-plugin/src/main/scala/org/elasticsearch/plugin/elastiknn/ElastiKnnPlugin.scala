package org.elasticsearch.plugin.elastiknn

import java.util
import java.util.function.Supplier

import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver
import org.elasticsearch.cluster.node.DiscoveryNodes
import org.elasticsearch.common.settings.{
  ClusterSettings,
  IndexScopedSettings,
  Settings,
  SettingsFilter
}
import org.elasticsearch.ingest.Processor
import org.elasticsearch.plugins.{ActionPlugin, IngestPlugin, Plugin}
import org.elasticsearch.rest.{RestController, RestHandler}

import scala.collection.JavaConverters._

class ElastiKnnPlugin extends Plugin with ActionPlugin with IngestPlugin {

  override def getRestHandlers(
      settings: Settings,
      restController: RestController,
      clusterSettings: ClusterSettings,
      indexScopedSettings: IndexScopedSettings,
      settingsFilter: SettingsFilter,
      indexNameExpressionResolver: IndexNameExpressionResolver,
      nodesInCluster: Supplier[DiscoveryNodes]): util.List[RestHandler] = {
    List.empty[RestHandler].asJava
  }

  override def getProcessors(
      parameters: Processor.Parameters): util.Map[String, Processor.Factory] = {
    Map.empty[String, Processor.Factory].asJava
  }

}
