package com.klibisz.elastiknn.engine

import com.klibisz.elastiknn.codec.ElastiknnCodecService
import org.elasticsearch.index.engine.{Engine, EngineConfig, EngineFactory, InternalEngine}

class ElastiknnEngineFactory extends EngineFactory {

  override def newReadWriteEngine(config: EngineConfig): Engine = new InternalEngine(
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
      ElastiknnEngineFactory.codecService,
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
    )
  )
}

object ElastiknnEngineFactory {
  private val codecService = new ElastiknnCodecService
}
