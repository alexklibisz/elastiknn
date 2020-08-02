package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, HashAndFreq, HashingFunction}
import com.klibisz.elastiknn.storage.{StoredVec, UnsafeSerialization}
import org.apache.lucene.document.Field
import org.apache.lucene.index.{IndexReader, IndexableField, LeafReaderContext}
import org.apache.lucene.search.{MatchHashesAndScoreQuery, Query}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.index.mapper.MappedFieldType

object HashingQuery {

  /**
    * Construct a hashing query.
    */
  def apply[V <: Vec, S <: StoredVec](field: String,
                                      query: V,
                                      candidates: Int,
                                      hashes: Array[HashAndFreq],
                                      exactFunction: ExactSimilarityFunction[V, S],
                                      indexReader: IndexReader)(implicit codec: StoredVec.Codec[V, S]): Query = {
    val scoreFunction: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
      (lrc: LeafReaderContext) => {
        val reader = new ExactQuery.StoredVecReader[S](lrc, field)
        (docId: Int, _: Int) =>
          val storedVec = reader(docId)
          exactFunction(query, storedVec)
      }
    new MatchHashesAndScoreQuery(
      field,
      hashes,
      candidates,
      indexReader,
      scoreFunction
    )
  }

  /**
    * Construct a hashing query.
    */
  def apply[M <: Mapping, V <: Vec, S <: StoredVec](field: String,
                                                    query: V,
                                                    candidates: Int,
                                                    lshFunction: HashingFunction[M, V, S],
                                                    exactFunction: ExactSimilarityFunction[V, S],
                                                    indexReader: IndexReader)(implicit codec: StoredVec.Codec[V, S]): Query =
    HashingQuery(field, query, candidates, lshFunction(query), exactFunction, indexReader)

  /**
    * Hash and construct IndexableFields for a vector using the given LSH function.
    */
  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec, F <: HashingFunction[M, V, S]](
      field: String,
      fieldType: MappedFieldType,
      vec: V,
      lshFunction: HashingFunction[M, V, S])(implicit lshFunctionCache: HashingFunctionCache[M, V, S, F]): Seq[IndexableField] = {
    val hashes = lshFunction(vec)
    ExactQuery.index(field, vec) ++ hashes.flatMap { h =>
      val f = new Field(field, h.getHash, fieldType)
      (0 until h.getFreq).map(_ => f)
    }
  }

}
