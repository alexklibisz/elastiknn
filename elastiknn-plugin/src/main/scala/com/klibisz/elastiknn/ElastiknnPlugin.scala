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

  override def getSettings: util.List[Setting[_]] = Collections.emptyList()

  override def getScoreFunctions: util.List[SearchPlugin.ScoreFunctionSpec[_]] =
    Collections.singletonList(
      new ScoreFunctionSpec(KnnScoreFunctionBuilder.NAME, KnnScoreFunctionBuilder.Reader, KnnScoreFunctionBuilder.Parser)
    )

  override def getEngineFactory(indexSettings: IndexSettings): Optional[EngineFactory] = {
    Optional.empty()
  }
}
