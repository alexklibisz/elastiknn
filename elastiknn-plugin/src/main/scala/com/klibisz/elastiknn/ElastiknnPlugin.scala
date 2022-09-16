package com.klibisz.elastiknn

import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.query._
import org.elasticsearch.common.settings.{Setting, Settings}
import org.elasticsearch.index.IndexSettings
import org.elasticsearch.index.engine.EngineFactory
import org.elasticsearch.index.mapper.Mapper
import org.elasticsearch.plugins.SearchPlugin.{QuerySpec, ScoreFunctionSpec}
import org.elasticsearch.plugins._

import java.util
import java.util.{Collections, Optional}

class ElastiknnPlugin(settings: Settings) extends Plugin with SearchPlugin with MapperPlugin with EnginePlugin {

  override def getQueries: util.List[SearchPlugin.QuerySpec[_]] =
    Collections.singletonList(
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
    if (indexSettings.getValue(ElastiknnPlugin.Settings.elastiknn)) Optional.empty()
    else Optional.empty()
  }
}

object ElastiknnPlugin {

  object Settings {

    // Setting: index.elastiknn
    // Previously used to determine whether elastiknn can control the codec used for the index to improve performance.
    // Now it's a no-op. 
    // It was deprecated as part of https://github.com/alexklibisz/elastiknn/issues/254 and 
    // https://github.com/alexklibisz/elastiknn/issues/348.
    val elastiknn: Setting[java.lang.Boolean] =
      Setting.boolSetting("index.elastiknn", false, Setting.Property.IndexScope)
  }
}
