package com.klibisz.elastiknn

import org.elasticsearch.ingest.Processor
import org.elasticsearch.plugins.{ActionPlugin, IngestPlugin, Plugin}
import java.util
import java.util.Collections.singletonMap
import java.util.function.Supplier

import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver
import org.elasticsearch.cluster.node.DiscoveryNodes
import org.elasticsearch.common.settings._
import org.elasticsearch.rest.{RestController, RestHandler}
import org.elasticsearch.threadpool.ThreadPool

class ElastiKnnPlugin extends Plugin with IngestPlugin with ActionPlugin {

  override def getProcessors(parameters: Processor.Parameters): util.Map[String, Processor.Factory] = {
    val threadPool: ThreadPool = new ThreadPool(parameters.env.settings())
    val nodeClient: NodeClient = new NodeClient(parameters.env.settings(), threadPool)
    singletonMap(ElastiKnnProcessor.TYPE, new ElastiKnnProcessor.Factory(nodeClient))
  }

  override def getRestHandlers(settings: Settings,
                               restController: RestController,
                               clusterSettings: ClusterSettings,
                               indexScopedSettings: IndexScopedSettings,
                               settingsFilter: SettingsFilter,
                               indexNameExpressionResolver: IndexNameExpressionResolver,
                               nodesInCluster: Supplier[DiscoveryNodes]): util.List[RestHandler] =
    util.Collections.emptyList()

}
