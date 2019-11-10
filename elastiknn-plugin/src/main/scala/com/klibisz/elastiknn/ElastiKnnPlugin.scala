package com.klibisz.elastiknn

import java.util
import java.util.Collections.singletonMap
import java.util.function.Supplier

import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.client.Client
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver
import org.elasticsearch.cluster.node.DiscoveryNodes
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.io.stream.NamedWriteableRegistry
import org.elasticsearch.common.settings.{ClusterSettings, IndexScopedSettings, Settings, SettingsFilter}
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.env.{Environment, NodeEnvironment}
import org.elasticsearch.ingest.Processor
import org.elasticsearch.plugins.SearchPlugin.QuerySpec
import org.elasticsearch.plugins.{ActionPlugin, IngestPlugin, Plugin, SearchPlugin}
import org.elasticsearch.rest.{RestController, RestHandler}
import org.elasticsearch.script.ScriptService
import org.elasticsearch.threadpool.ThreadPool
import org.elasticsearch.watcher.ResourceWatcherService

class ElastiKnnPlugin(settings: Settings) extends Plugin with IngestPlugin with SearchPlugin with ActionPlugin {

  private val logger: Logger = LogManager.getLogger(getClass)

  override def createComponents(client: Client,
                                clusterService: ClusterService,
                                threadPool: ThreadPool,
                                resourceWatcherService: ResourceWatcherService,
                                scriptService: ScriptService,
                                xContentRegistry: NamedXContentRegistry,
                                environment: Environment,
                                nodeEnvironment: NodeEnvironment,
                                namedWriteableRegistry: NamedWriteableRegistry): util.Collection[Object] = {
    SharedClient.set(client)
    util.Collections.emptyList[Object]()
  }

  override def getProcessors(parameters: Processor.Parameters): util.Map[String, Processor.Factory] = {
    val threadPool = new ThreadPool(parameters.env.settings())
    val client = new NodeClient(parameters.env.settings(), threadPool)
    singletonMap(IngestProcessor.TYPE, new IngestProcessor.Factory(client))
  }

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] = util.Arrays.asList(
    new QuerySpec(KnnQueryBuilder.NAME, KnnQueryBuilder.Reader, KnnQueryBuilder.Parser),
    new QuerySpec(RadiusQuery.NAME, RadiusQuery.Reader, RadiusQuery.Parser)
  )

  override def getRestHandlers(settings: Settings,
                               restController: RestController,
                               clusterSettings: ClusterSettings,
                               indexScopedSettings: IndexScopedSettings,
                               settingsFilter: SettingsFilter,
                               indexNameExpressionResolver: IndexNameExpressionResolver,
                               nodesInCluster: Supplier[DiscoveryNodes]): util.List[RestHandler] =
    util.Arrays.asList(new rest.SetupRestAction(restController))

}
