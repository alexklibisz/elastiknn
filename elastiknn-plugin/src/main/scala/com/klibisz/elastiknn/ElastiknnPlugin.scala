package com.klibisz.elastiknn

import java.util

import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.query._
import com.klibisz.elastiknn.storage.{StoredVec, VectorCache}
import org.elasticsearch.common.settings.{Setting, Settings}
import org.elasticsearch.index.mapper.Mapper
import org.elasticsearch.common.settings.Setting.Property.{Final, NodeScope}
import org.elasticsearch.plugins.SearchPlugin.QuerySpec
import org.elasticsearch.plugins._

class ElastiknnPlugin(settings: Settings) extends Plugin with IngestPlugin with SearchPlugin with ActionPlugin with MapperPlugin {

  // Cache settings. The caches are instantiated in `getQueries` based on these settings.
  private val cacheEnabled = Setting.boolSetting("elastiknn.cache.enabled", false, NodeScope, Final)
  private val cacheCapacityMb = Setting.longSetting("elastiknn.cache.capacity_mb", 512, 0, NodeScope, Final)
  private val cacheTTLSeconds = Setting.longSetting("elastiknn.cache.ttl_seconds", 600, 0, NodeScope, Final)

  override def getSettings: util.List[Setting[_]] = util.List.of(cacheEnabled, cacheCapacityMb, cacheTTLSeconds)

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] = {
    val processors: Int = Runtime.getRuntime.availableProcessors()
    val denseCache: VectorCache[StoredVec.DenseFloat] =
      if (cacheEnabled.get(settings)) VectorCache(processors, cacheCapacityMb.get(settings), cacheTTLSeconds.get(settings))
      else VectorCache.empty
    val sparseCache: VectorCache[StoredVec.SparseBool] =
      if (cacheEnabled.get(settings)) VectorCache(processors, cacheCapacityMb.get(settings), cacheTTLSeconds.get(settings))
      else VectorCache.empty
    util.Arrays.asList(
      new QuerySpec(KnnQueryBuilder.NAME,
                    new KnnQueryBuilder.Reader(denseCache, sparseCache),
                    new KnnQueryBuilder.Parser(denseCache, sparseCache))
    )
  }

  override def getMappers: util.Map[String, Mapper.TypeParser] =
    new util.HashMap[String, Mapper.TypeParser] {
      put(VectorMapper.sparseBoolVector.CONTENT_TYPE, new VectorMapper.sparseBoolVector.TypeParser)
      put(VectorMapper.denseFloatVector.CONTENT_TYPE, new VectorMapper.denseFloatVector.TypeParser)
    }

}
