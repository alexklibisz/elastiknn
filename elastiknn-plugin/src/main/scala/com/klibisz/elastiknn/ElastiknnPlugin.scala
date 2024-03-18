package com.klibisz.elastiknn

import com.klibisz.elastiknn.mapper.VectorMapper.{DenseFloatVectorMapper, SparseBoolVectorMapper}
import com.klibisz.elastiknn.models.ModelCache
import com.klibisz.elastiknn.query.*
import com.klibisz.elastiknn.vectors.{DefaultFloatVectorOps, FloatVectorOps, PanamaFloatVectorOps}
import org.elasticsearch.common.settings.{Setting, Settings}
import org.elasticsearch.index.IndexSettings
import org.elasticsearch.index.engine.EngineFactory
import org.elasticsearch.index.mapper.Mapper
import org.elasticsearch.plugins.SearchPlugin.{QuerySpec, ScoreFunctionSpec}
import org.elasticsearch.plugins.*

import java.security.{AccessController, PrivilegedAction}
import java.util
import java.util.{Collections, Optional}

class ElastiknnPlugin(settings: Settings) extends Plugin with SearchPlugin with MapperPlugin with EnginePlugin {

  import ElastiknnPlugin.Settings

  import scala.runtime.LazyVals.Evaluating
  println(Evaluating)

  private val floatVectorOps: FloatVectorOps =
    if (Settings.jdkIncubatorVectorEnabledSetting.get(settings)) new PanamaFloatVectorOps
    else new DefaultFloatVectorOps
  private val modelCache = new ModelCache(floatVectorOps)
  private val elastiknnQueryBuilder: ElastiknnQueryBuilder = new ElastiknnQueryBuilder(floatVectorOps, modelCache)

  // The doPrivileged is needed because scala.runtime.LazyVals uses sun.misc.unsafe, and somehow LazyVals is
  // invoked while instantiating a class that extends an abstract class.
  // See plugin-security.policy for the extra permissions needed to invoke this code.
  // See https://github.com/scala/scala3/issues/9013 for details on LazyVals and sun.misc.unsafe.
  private val denseFloatVectorMapper = AccessController.doPrivileged(new PrivilegedAction[DenseFloatVectorMapper] {
    override def run(): DenseFloatVectorMapper =
      new DenseFloatVectorMapper(modelCache)
  })
  private val sparseBoolVectorMapper = AccessController.doPrivileged(new PrivilegedAction[SparseBoolVectorMapper] {
    override def run(): SparseBoolVectorMapper =
      new SparseBoolVectorMapper(modelCache)
  })

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
