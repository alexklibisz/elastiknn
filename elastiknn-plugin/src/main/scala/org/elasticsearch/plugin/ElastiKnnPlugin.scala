package org.elasticsearch.plugin

import org.elasticsearch.ingest.Processor
import org.elasticsearch.plugins.{ActionPlugin, IngestPlugin, Plugin}
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
import org.elasticsearch.rest.{RestController, RestHandler}

class ElastiKnnPlugin extends Plugin with IngestPlugin with ActionPlugin {
  override def getProcessors(
      parameters: Processor.Parameters): util.Map[String, Processor.Factory] =
    util.Collections.emptyMap()

  override def getRestHandlers(
      settings: Settings,
      restController: RestController,
      clusterSettings: ClusterSettings,
      indexScopedSettings: IndexScopedSettings,
      settingsFilter: SettingsFilter,
      indexNameExpressionResolver: IndexNameExpressionResolver,
      nodesInCluster: Supplier[DiscoveryNodes]): util.List[RestHandler] =
    util.Collections.emptyList()

}
