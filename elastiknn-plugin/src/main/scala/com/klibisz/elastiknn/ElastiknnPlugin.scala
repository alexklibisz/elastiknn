package com.klibisz.elastiknn

import java.util
import java.util.Optional

import com.klibisz.elastiknn.engine.ElastiknnEngineFactory
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.query._
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.io.stream.NamedWriteableRegistry
import org.elasticsearch.common.settings.{Setting, Settings}
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.env.{Environment, NodeEnvironment}
import org.elasticsearch.index.IndexSettings
import org.elasticsearch.index.engine.{Engine, EngineConfig, EngineFactory}
import org.elasticsearch.index.mapper.Mapper
import org.elasticsearch.plugins.SearchPlugin.QuerySpec
import org.elasticsearch.plugins._
import org.elasticsearch.script.ScriptService
import org.elasticsearch.threadpool.ThreadPool
import org.elasticsearch.watcher.ResourceWatcherService

class ElastiknnPlugin(settings: Settings)
    extends Plugin
    with IngestPlugin
    with SearchPlugin
    with ActionPlugin
    with MapperPlugin
    with EnginePlugin {

  private val settingInMemory = Setting.boolSetting("index.elastiknn_in_memory", false, Setting.Property.IndexScope)

  override def getSettings: util.List[Setting[_]] = util.List.of[Setting[_]](settingInMemory)

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

  override def getEngineFactory(indexSettings: IndexSettings): Optional[EngineFactory] = {
    if (indexSettings.getValue(settingInMemory)) Optional.of(new ElastiknnEngineFactory) else Optional.empty()
  }
}
