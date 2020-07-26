package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, HashingFunction}
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
  def apply[M <: Mapping, V <: Vec, S <: StoredVec](field: String,
                                                    query: V,
                                                    candidates: Int,
                                                    lshFunction: HashingFunction[M, V, S],
                                                    exactFunction: ExactSimilarityFunction[V, S],
                                                    indexReader: IndexReader)(implicit codec: StoredVec.Codec[V, S]): Query = {
    val hashes = lshFunction(query).map(new BytesRef(_))
    val scoreFunction: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
      (lrc: LeafReaderContext) => {
        val cachedReader = new ExactQuery.StoredVecReader[S](lrc, field)
        (docId: Int, _: Int) =>
          val storedVec = cachedReader(docId)
          val exactScore = exactFunction(query, storedVec)
          exactScore
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
    * Hash and construct IndexableFields for a vector using the given LSH function.
    */
  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec](
      field: String,
      fieldType: MappedFieldType,
      vec: V,
      lshFunction: HashingFunction[M, V, S])(implicit lshFunctionCache: HashingFunctionCache[M, V, S]): Seq[IndexableField] = {
    val hashes = lshFunction(vec)
//    System.out.println(s"EKNNDEBUG: ${hashes.map(UnsafeSerialization.readInt).mkString(",")}")
//    System.out.println("")
    ExactQuery.index(field, vec) ++ hashes.distinct.map { h =>
      new Field(field, h, fieldType)
    }
  }
}
