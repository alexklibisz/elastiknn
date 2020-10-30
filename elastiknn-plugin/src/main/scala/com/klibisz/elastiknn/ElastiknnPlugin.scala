package com.klibisz.elastiknn

import java.{lang, util}
import java.util.Optional

import com.klibisz.elastiknn.codec.ElastiknnCodecService
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.query._
import org.elasticsearch.common.settings.{Setting, Settings}
import org.elasticsearch.index.IndexSettings
import org.elasticsearch.index.engine.{Engine, EngineConfig, EngineFactory, InternalEngine}
import org.elasticsearch.index.mapper.Mapper
import org.elasticsearch.plugins.SearchPlugin.QuerySpec
import org.elasticsearch.plugins._

class ElastiknnPlugin(settings: Settings) extends Plugin with IngestPlugin with SearchPlugin with MapperPlugin with EnginePlugin {

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] = util.Arrays.asList(
    new QuerySpec(KnnQueryBuilder.NAME, KnnQueryBuilder.Reader, KnnQueryBuilder.Parser)
  )

  override def getMappers: util.Map[String, Mapper.TypeParser] = {
    import VectorMapper._
    new util.HashMap[String, Mapper.TypeParser] {
      put(sparseBoolVector.CONTENT_TYPE, new sparseBoolVector.TypeParser)
      put(denseFloatVector.CONTENT_TYPE, new denseFloatVector.TypeParser)
    }
  }

  override def getSettings: util.List[Setting[_]] = util.List.of[Setting[_]](
    ElastiknnPlugin.Settings.elastiknnFastDocValues
  )

  override def getEngineFactory(indexSettings: IndexSettings): Optional[EngineFactory] =
    Optional.of {
      new EngineFactory {
        val codecService = new ElastiknnCodecService
        override def newReadWriteEngine(config: EngineConfig): Engine = {
          new InternalEngine(
            new EngineConfig(
              config.getShardId,
              config.getAllocationId,
              config.getThreadPool,
              config.getIndexSettings,
              config.getWarmer,
              config.getStore,
              config.getMergePolicy,
              config.getAnalyzer,
              config.getSimilarity,
              codecService,
              config.getEventListener,
              config.getQueryCache,
              config.getQueryCachingPolicy,
              config.getTranslogConfig,
              config.getFlushMergesAfter,
              config.getExternalRefreshListener,
              config.getInternalRefreshListener,
              config.getIndexSort,
              config.getCircuitBreakerService,
              config.getGlobalCheckpointSupplier,
              config.retentionLeasesSupplier,
              config.getPrimaryTermSupplier,
              config.getTombstoneDocSupplier
            ))
        }
      }
    }
//    if (indexSettings.getValue(ElastiknnPlugin.Settings.elastiknnFastDocValues)) {
//      ???
//    } else Optional.empty[EngineFactory]
}

object ElastiknnPlugin {

  object Settings {
    val elastiknnFastDocValues: Setting[lang.Boolean] =
      Setting.boolSetting("index.elastiknn.fast_doc_values", true, Setting.Property.IndexScope)
  }

}
