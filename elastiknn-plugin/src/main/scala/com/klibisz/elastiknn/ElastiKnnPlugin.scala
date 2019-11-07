package com.klibisz.elastiknn

import java.util
import java.util.Collections.singletonMap

import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.ingest.Processor
import org.elasticsearch.plugins.SearchPlugin.QuerySpec
import org.elasticsearch.plugins.{IngestPlugin, Plugin, SearchPlugin}
import org.elasticsearch.threadpool.ThreadPool

class ElastiKnnPlugin extends Plugin with IngestPlugin with SearchPlugin {

  override def getProcessors(parameters: Processor.Parameters): util.Map[String, Processor.Factory] = {
    val threadPool: ThreadPool = new ThreadPool(parameters.env.settings())
    val nodeClient: NodeClient = new NodeClient(parameters.env.settings(), threadPool)
    singletonMap(IngestProcessor.TYPE, new IngestProcessor.Factory(nodeClient))
  }

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] = util.Arrays.asList(
    new QuerySpec(KnnQueryBuilder.NAME, KnnQueryBuilder.Reader, KnnQueryBuilder.Parser),
    new QuerySpec(RadiusQuery.NAME, RadiusQuery.Reader, RadiusQuery.Parser)
  )

}
