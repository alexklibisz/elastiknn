package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.{ELASTIKNN_NAME, storage}
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.IndexableField
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.index.query.QueryShardContext

class ExactSimilarityQuery() extends Query {
  override def toString(field: String): String = ???
  override def equals(obj: Any): Boolean = ???
  override def hashCode(): Int = ???
}

object ExactSimilarityQuery {

  val FIELD_NAME: String = s"${ELASTIKNN_NAME}.vector"

  def index(sbv: Vec.SparseBool): Seq[IndexableField] = {
    val stored = storage.SparseBoolVector(sbv.trueIndices, sbv.totalIndices)
    Seq(new BinaryDocValuesField(FIELD_NAME, new BytesRef(stored.toByteArray)))
  }

  def jaccard(c: QueryShardContext, field: String, v: Vec.SparseBool): Query = new DocValuesFieldExistsQuery(FIELD_NAME)

  def hamming(c: QueryShardContext, field: String, v: Vec.SparseBool): Query = new DocValuesFieldExistsQuery(FIELD_NAME)

  def l1(c: QueryShardContext, field: String, v: Vec.DenseFloat): Query = new DocValuesFieldExistsQuery(FIELD_NAME)

  def l2(c: QueryShardContext, field: String, v: Vec.DenseFloat): Query = new DocValuesFieldExistsQuery(FIELD_NAME)

  def angular(c: QueryShardContext, field: String, v: Vec.DenseFloat): Query = new DocValuesFieldExistsQuery(FIELD_NAME)

}
