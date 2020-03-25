package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.Vec
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.elasticsearch.index.query.QueryShardContext

class JaccardIndexedQuery() extends Query {
  override def toString(field: String): String = ???
  override def equals(obj: Any): Boolean = ???
  override def hashCode(): Int = ???
}

object JaccardIndexedQuery {
  def apply(c: QueryShardContext, field: String, v: Vec.SparseBool): Query =
    new DocValuesFieldExistsQuery(field)
}
