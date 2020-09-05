package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, HashAndFreq, HashingFunction}
import com.klibisz.elastiknn.storage.StoredVec
import org.apache.lucene.document.Field
import org.apache.lucene.index.{IndexReader, IndexableField, LeafReaderContext}
import org.apache.lucene.search.{MatchHashesAndScoreQuery, Query}
import org.elasticsearch.index.mapper.MappedFieldType

object HashingQuery {

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

  def apply[M <: Mapping, V <: Vec, S <: StoredVec](field: String,
                                                    query: V,
                                                    candidates: Int,
                                                    lshFunction: HashingFunction[M, V, S],
                                                    exactFunction: ExactSimilarityFunction[V, S],
                                                    indexReader: IndexReader)(implicit codec: StoredVec.Codec[V, S]): Query =
    HashingQuery(field, query, candidates, lshFunction(query), exactFunction, indexReader)

  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec, F <: HashingFunction[M, V, S]](
      field: String,
      fieldType: MappedFieldType,
      vec: V,
      hashes: Array[HashAndFreq]): Seq[IndexableField] = ExactQuery.index(field, vec) ++ hashes.flatMap { h =>
    val f = new Field(field, h.getHash, fieldType)
    (0 until h.getFreq).map(_ => f)
  }

}
