package com.klibisz.elastiknn

import java.util
import java.util.Collections.singletonMap
import java.util.function.Supplier

import org.elasticsearch.common.settings.{ClusterSettings, IndexScopedSettings, Settings, SettingsFilter}
import com.klibisz.elastiknn.mapper.{ElastiKnnVectorFieldMapper, VectorMapper}
import com.klibisz.elastiknn.processor.IngestProcessor
import com.klibisz.elastiknn.query._
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver
import org.elasticsearch.cluster.node.DiscoveryNodes
import org.elasticsearch.index.mapper.Mapper
import org.elasticsearch.ingest.Processor
import org.elasticsearch.plugins.SearchPlugin.QuerySpec
import org.elasticsearch.plugins._
import org.elasticsearch.rest.{RestController, RestHandler, RestRequest}

import scala.collection.JavaConverters._

class ElastiKnnPlugin(settings: Settings) extends Plugin with IngestPlugin with SearchPlugin with ActionPlugin with MapperPlugin {

  override def getRestHandlers(settings: Settings,
                               restController: RestController,
                               clusterSettings: ClusterSettings,
                               indexScopedSettings: IndexScopedSettings,
                               settingsFilter: SettingsFilter,
                               indexNameExpressionResolver: IndexNameExpressionResolver,
                               nodesInCluster: Supplier[DiscoveryNodes]): util.List[RestHandler] = {
    val handler = new rest.PrepareMappingHandler()
    restController.registerHandler(RestRequest.Method.PUT, s"_$ELASTIKNN_NAME/prepare_mapping", handler)
    util.Arrays.asList(handler)
  }

  override def getProcessors(parameters: Processor.Parameters): util.Map[String, Processor.Factory] =
    singletonMap(IngestProcessor.TYPE, new IngestProcessor.Factory)

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] = util.Arrays.asList(
    new QuerySpec(KnnQueryBuilder.NAME, KnnQueryBuilder.Reader, KnnQueryBuilder.Parser)
//    new QuerySpec(KnnQueryBuilderOld.NAME, KnnQueryBuilderOld.Reader, KnnQueryBuilderOld.Parser),
//    new QuerySpec(ExactComputedQueryBuilder.NAME, ExactComputedQueryBuilder.Reader, ExactComputedQueryBuilder.Parser),
//    new QuerySpec(JaccardIndexedQueryBuilder.NAME, JaccardIndexedQueryBuilder.Reader, JaccardIndexedQueryBuilder.Parser),
//    new QuerySpec(JaccardLshQueryBuilder.NAME, JaccardLshQueryBuilder.Reader, JaccardLshQueryBuilder.Parser),
//    new QuerySpec(RadiusQueryBuilder.NAME, RadiusQueryBuilder.Reader, RadiusQueryBuilder.Parser)
  )

  override def getMappers: util.Map[String, Mapper.TypeParser] = {
    import VectorMapper._
    Map(
      ElastiKnnVectorFieldMapper.CONTENT_TYPE -> new ElastiKnnVectorFieldMapper.TypeParser,
      sparseBoolVector.CONTENT_TYPE -> new sparseBoolVector.TypeParser,
      denseFloatVector.CONTENT_TYPE -> new denseFloatVector.TypeParser
    ).asJava
  }

}
