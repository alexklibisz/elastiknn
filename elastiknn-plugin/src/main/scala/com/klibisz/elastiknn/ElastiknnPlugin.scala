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

  import ElastiknnPlugin.Settings

  private val floatVectorOps: FloatVectorOps =
    if (Settings.jdkIncubatorVectorEnabledSetting.get(settings)) new PanamaFloatVectorOps
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
    Settings.elastiknn,
    Settings.jdkIncubatorVectorEnabledSetting
  )

  override def getScoreFunctions: util.List[SearchPlugin.ScoreFunctionSpec[_]] =
    Collections.singletonList(
      new ScoreFunctionSpec(
        ElasticsearchScoreFunctionBuilder.NAME,
        new ElasticsearchScoreFunctionBuilder.Reader(elastiknnQueryBuilder),
        new ElasticsearchScoreFunctionBuilder.Parser(elastiknnQueryBuilder)
      )
    )

  override def getEngineFactory(indexSettings: IndexSettings): Optional[EngineFactory] = {
    Optional.empty()
  }
}

object ElastiknnPlugin {
  object Settings {

    // Deprecated setting.
    // Previously used to determine whether elastiknn can control the codec used for the index to improve performance.
    // Now it's a no-op. Deprecated as part of https://github.com/alexklibisz/elastiknn/issues/254 and
    // https://github.com/alexklibisz/elastiknn/issues/348.
    val elastiknn: Setting[java.lang.Boolean] =
      Setting.boolSetting("index.elastiknn", false, Setting.Property.IndexScope, Setting.Property.Deprecated)

    val jdkIncubatorVectorEnabledSetting: Setting[java.lang.Boolean] =
      Setting.boolSetting("elastiknn.jdk-incubator-vector.enabled", false, Setting.Property.NodeScope)
  }
}
