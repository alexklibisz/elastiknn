package com.klibisz.elastiknn

import java.util
import java.util.Collections.singletonMap

import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptAction
import org.elasticsearch.client.Client
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.cluster.{ClusterChangedEvent, ClusterStateListener}
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

    // Create scripts. Called inside a listener; otherwise you get an error: "initial cluster state not set yet"
    // Originally this was inside a LifecycleComponent, but that doesn't seem to be necessary.
    clusterService.addListener(new ClusterStateListener {
      override def clusterChanged(event: ClusterChangedEvent): Unit = {
        Seq(StoredScripts.exactAngular).foreach { s =>
          logger.info(s"Creating stored script ${s.id}")
          client.execute(PutStoredScriptAction.INSTANCE, s.putRequest)
        }
        clusterService.removeListener(this)
      }
    })

    util.Collections.emptyList[Object]()
  }

  override def getProcessors(parameters: Processor.Parameters): util.Map[String, Processor.Factory] = {
    val threadPool = new ThreadPool(parameters.env.settings())
    val nodeClient = new NodeClient(parameters.env.settings(), threadPool)
    singletonMap(IngestProcessor.TYPE, new IngestProcessor.Factory(nodeClient))
  }

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] = util.Arrays.asList(
    new QuerySpec(KnnQueryBuilder.NAME, KnnQueryBuilder.Reader, KnnQueryBuilder.Parser),
//    new QuerySpec(KnnQueryBuilder.NAME, in => new MatchAllQueryBuilder(in), xc => MatchAllQueryBuilder.fromXContent(xc)),
//    new QuerySpec(RadiusQuery.NAME, RadiusQuery.Reader, RadiusQuery.Parser)
  )

  // TODO: maybe there's another method you have to override to get the query results out?

}
