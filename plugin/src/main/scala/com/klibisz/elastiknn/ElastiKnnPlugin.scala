package com.klibisz.elastiknn

import java.util

import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.query._
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.mapper.Mapper
import org.elasticsearch.plugins.SearchPlugin.QuerySpec
import org.elasticsearch.plugins._

class ElastiKnnPlugin(settings: Settings) extends Plugin with IngestPlugin with SearchPlugin with ActionPlugin with MapperPlugin {

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

}
