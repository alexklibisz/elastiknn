package com.klibisz.elastiknn

import java.util
import java.util.{Collections, Optional}

import com.klibisz.elastiknn.codec.ElastiknnCodecService
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.query._
import org.elasticsearch.common.settings.{Setting, Settings}
import org.elasticsearch.index.IndexSettings
import org.elasticsearch.index.engine.{Engine, EngineConfig, EngineFactory, InternalEngine}
import org.elasticsearch.index.mapper.Mapper
import org.elasticsearch.plugins.SearchPlugin.{QuerySpec, ScoreFunctionSpec}
import org.elasticsearch.plugins._

class ElastiknnPlugin(settings: Settings) extends Plugin with SearchPlugin with MapperPlugin with EnginePlugin {

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] = Collections.singletonList(
    new QuerySpec(KnnQueryBuilder.NAME, KnnQueryBuilder.Reader, KnnQueryBuilder.Parser)
  )

  override def getMappers: util.Map[String, Mapper.TypeParser] = {
    import VectorMapper._
    new util.HashMap[String, Mapper.TypeParser] {
      put(sparseBoolVector.CONTENT_TYPE, new sparseBoolVector.TypeParser)
      put(denseFloatVector.CONTENT_TYPE, new denseFloatVector.TypeParser)
    }
  }

  override def getSettings: util.List[Setting[_]] = Collections.singletonList(ElastiknnPlugin.Settings.elastiknn)

  override def getScoreFunctions: util.List[SearchPlugin.ScoreFunctionSpec[_]] =
    Collections.singletonList(
      new ScoreFunctionSpec(KnnScoreFunctionBuilder.NAME, KnnScoreFunctionBuilder.Reader, KnnScoreFunctionBuilder.Parser)
    )

  override def getEngineFactory(indexSettings: IndexSettings): Optional[EngineFactory] = {
    if (indexSettings.getValue(ElastiknnPlugin.Settings.elastiknn)) Optional.of {
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
    } else Optional.empty()
  }
}

object ElastiknnPlugin {

  object Settings {

    // Setting: index.elastiknn
    // Determines whether elastiknn can control the codec used for the index.
    // Highly recommended to set to true. Elastiknn will still work without it, but will be much slower.
    val elastiknn: Setting[java.lang.Boolean] =
      Setting.boolSetting("index.elastiknn", false, Setting.Property.IndexScope)
  }

}
