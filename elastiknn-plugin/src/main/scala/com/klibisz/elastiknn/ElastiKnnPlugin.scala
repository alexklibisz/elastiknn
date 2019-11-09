package com.klibisz.elastiknn

import java.util
import java.util.Collections.singletonMap

import org.elasticsearch.client.Client
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.io.stream.NamedWriteableRegistry
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.env.{Environment, NodeEnvironment}
import org.elasticsearch.ingest.Processor
import org.elasticsearch.plugins.SearchPlugin.QuerySpec
import org.elasticsearch.plugins.{IngestPlugin, Plugin, SearchPlugin}
import org.elasticsearch.script.ScriptService
import org.elasticsearch.threadpool.ThreadPool
import org.elasticsearch.watcher.ResourceWatcherService

class ElastiKnnPlugin extends Plugin with IngestPlugin with SearchPlugin {

  override def createComponents(client: Client,
                                clusterService: ClusterService,
                                threadPool: ThreadPool,
                                resourceWatcherService: ResourceWatcherService,
                                scriptService: ScriptService,
                                xContentRegistry: NamedXContentRegistry,
                                environment: Environment,
                                nodeEnvironment: NodeEnvironment,
                                namedWriteableRegistry: NamedWriteableRegistry): util.Collection[Object] = {
    util.Arrays.asList(new ElastiKnnLifecycleComponent(client, clusterService))
  }

  override def getProcessors(parameters: Processor.Parameters): util.Map[String, Processor.Factory] = {
    val threadPool = new ThreadPool(parameters.env.settings())
    val nodeClient = new NodeClient(parameters.env.settings(), threadPool)
    singletonMap(IngestProcessor.TYPE, new IngestProcessor.Factory(nodeClient))
  }

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] = util.Arrays.asList(
    new QuerySpec(KnnQueryBuilder.NAME, KnnQueryBuilder.Reader, KnnQueryBuilder.Parser),
    new QuerySpec(RadiusQuery.NAME, RadiusQuery.Reader, RadiusQuery.Parser)
  )

}
