package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, HashAndFreq, HashingFunction}
import com.klibisz.elastiknn.storage.{StoredVec, StoredVecReader}
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index.{IndexableField, LeafReaderContext}
import org.apache.lucene.search.{MatchHashesAndScoreQuery, Query}
import org.elasticsearch.common.lucene.search.function.ScoreFunction
import org.elasticsearch.index.query.QueryShardContext

class HashingQuery[V <: Vec, S <: StoredVec](field: String,
                                             queryVec: V,
                                             candidates: Int,
                                             limit: Float,
                                             hashes: Array[HashAndFreq],
                                             simFunc: ExactSimilarityFunction[V, S])(implicit codec: StoredVec.Codec[V, S])
    extends ElastiknnQuery[V] {
  override def toLuceneQuery(queryShardContext: QueryShardContext): Query = {
    val scoreFunction: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
      (lrc: LeafReaderContext) => {
        val reader = new StoredVecReader[S](lrc, field)
        (docId: Int, _: Int) =>
          val storedVec = reader(docId)
          simFunc(queryVec, storedVec)
      }
    new MatchHashesAndScoreQuery(
      field,
      hashes,
      candidates,
      limit,
      queryShardContext.getIndexReader,
      scoreFunction
    )
  }

  override def toScoreFunction(queryShardContext: QueryShardContext): ScoreFunction = ???
}

object HashingQuery {

  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec, F <: HashingFunction[M, V, S]](
      field: String,
      fieldType: FieldType,
      vec: V,
      hashes: Array[HashAndFreq]): Seq[IndexableField] = ExactQuery.index(field, vec) ++ hashes.flatMap { h =>
    val f = new Field(field, h.hash, fieldType)
    (0 until h.freq).map(_ => f)
  }

}
