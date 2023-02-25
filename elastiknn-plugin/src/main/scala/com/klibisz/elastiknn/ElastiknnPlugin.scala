package com.klibisz.elastiknn

import com.klibisz.elastiknn.mapper.VectorMapper.{DenseFloatVectorMapper, SparseBoolVectorMapper}
import com.klibisz.elastiknn.models.ModelCache
import com.klibisz.elastiknn.query._
import com.klibisz.elastiknn.vectors.{DefaultFloatVectorOps, FloatVectorOps, PanamaFloatVectorOps}
import org.elasticsearch.common.settings.{Setting, Settings}
import org.elasticsearch.index.IndexSettings
import org.elasticsearch.index.engine.EngineFactory
import org.elasticsearch.index.mapper.Mapper
import org.elasticsearch.plugins.SearchPlugin.{QuerySpec, ScoreFunctionSpec}
import org.elasticsearch.plugins._

import java.util
import java.util.{Collections, Optional}

class ElastiknnPlugin(settings: Settings) extends Plugin with SearchPlugin with MapperPlugin with EnginePlugin {

  private val floatVectorOps: FloatVectorOps =
    if (ElastiknnPlugin.jdkIncubatorVectorEnabled.get(settings)) new PanamaFloatVectorOps
    else new DefaultFloatVectorOps
  private val modelCache = new ModelCache(floatVectorOps)
  private val elastiknnQueryBuilder: ElastiknnQueryBuilder = new ElastiknnQueryBuilder(floatVectorOps, modelCache)
  private val sparseBoolVectorMapper = new SparseBoolVectorMapper(modelCache)
  private val denseFloatVectorMapper = new DenseFloatVectorMapper(modelCache)

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] = {
    Collections.singletonList(
      new QuerySpec(
        ElasticsearchQueryBuilder.NAME,
        new ElasticsearchQueryBuilder.Reader(elastiknnQueryBuilder),
        new ElasticsearchQueryBuilder.Parser(elastiknnQueryBuilder)
      )
    )
  }

  override def getMappers: util.Map[String, Mapper.TypeParser] = {
    new util.HashMap[String, Mapper.TypeParser] {
      put(sparseBoolVectorMapper.CONTENT_TYPE, sparseBoolVectorMapper.TypeParser)
      put(denseFloatVectorMapper.CONTENT_TYPE, denseFloatVectorMapper.TypeParser)
    }
  }

  override def getSettings: util.List[Setting[_]] = util.List.of(
    ElastiknnPlugin.jdkIncubatorVectorEnabled
  )

  override def getScoreFunctions: util.List[SearchPlugin.ScoreFunctionSpec[_]] =
    Collections.singletonList(
      new ScoreFunctionSpec(
        KnnScoreFunctionBuilder.NAME,
        new KnnScoreFunctionBuilder.Reader(elastiknnQueryBuilder),
        new KnnScoreFunctionBuilder.Parser(elastiknnQueryBuilder)
      )
    )

  override def getEngineFactory(indexSettings: IndexSettings): Optional[EngineFactory] = {
    Optional.empty()
  }
}

object ElastiknnPlugin {
  val jdkIncubatorVectorEnabled: Setting[java.lang.Boolean] =
    Setting.boolSetting("elastiknn.jdk-incubator-vector.enabled", false, Setting.Property.NodeScope)
}
