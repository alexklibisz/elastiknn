package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, HashingFunction}
import com.klibisz.elastiknn.storage.StoredVec
import org.apache.lucene.document.Field
import org.apache.lucene.index.{IndexReader, IndexableField, LeafReaderContext}
import org.apache.lucene.search.{MatchTermsAndScoreQuery, Query}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.index.mapper.MappedFieldType

object HashingQuery {

  /**
    * Construct a hashing query.
    */
  def apply[M <: Mapping, V <: Vec, S <: StoredVec](field: String,
                                                    query: V,
                                                    candidates: Int,
                                                    lshFunction: HashingFunction[M, V, S],
                                                    exactFunction: ExactSimilarityFunction[V, S],
                                                    indexReader: IndexReader)(implicit codec: StoredVec.Codec[V, S]): Query = {

    val terms = lshFunction(query).map(h => new BytesRef(h))

    val scoreFunction = (lrc: LeafReaderContext) => {
      val cachedReader = new ExactQuery.StoredVecReader[S](lrc, field)
      (docId: Int, _: Int) =>
        val storedVec = cachedReader(docId)
        exactFunction(query, storedVec)
    }

    new MatchTermsAndScoreQuery(
      field,
      terms,
      candidates,
      scoreFunction,
      indexReader
    )
  }

  /**
    * Hash and construct IndexableFields for a vector using the given LSH function.
    */
  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec](
      field: String,
      fieldType: MappedFieldType,
      vec: V,
      lshFunction: HashingFunction[M, V, S])(implicit lshFunctionCache: HashingFunctionCache[M, V, S]): Seq[IndexableField] = {
    ExactQuery.index(field, vec) ++ lshFunction(vec).map { h =>
      new Field(field, h, fieldType)
    }
  }
}
