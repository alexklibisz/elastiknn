package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.{Mapping, QueryOptions, Vec}
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.elasticsearch.index.query.QueryShardContext

class JaccardLshQuery() extends Query {
  override def toString(field: String): String = ???
  override def equals(obj: Any): Boolean = ???
  override def hashCode(): Int = ???
}

object JaccardLshQuery {
  def apply(context: QueryShardContext,
            field: String,
            mapping: Mapping.JaccardLsh,
            qopts: QueryOptions.JaccardLsh,
            vec: Vec.SparseBool): Query = new DocValuesFieldExistsQuery(ExactSimilarityQuery.storedVectorField(field))
}
