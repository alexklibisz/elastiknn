package com.klibisz.elastiknn.query

import java.time.Duration
import java.util.Optional

import com.google.common.cache.CacheBuilder
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, HashAndFreq, HashingFunction}
import com.klibisz.elastiknn.storage.StoredVec
import org.apache.lucene.document.Field
import org.apache.lucene.index.{IndexReader, IndexableField, LeafReaderContext}
import org.apache.lucene.search.MatchHashesAndScoreQuery.HitsCache
import org.apache.lucene.search.{MatchHashesAndScoreQuery, Query}
import org.elasticsearch.index.mapper.MappedFieldType

object HashingQuery {

  // TODO: this needs to be separated by index/index reader.
  val hitsCache: HitsCache = new HitsCache {
    private val c = CacheBuilder
      .newBuilder()
      .expireAfterWrite(Duration.ofMinutes(1))
      .build[HitsCache.Key, Array[HitsCache.Value]]
    override def get(key: HitsCache.Key): Optional[Array[HitsCache.Value]] = {
      val maybe = c.getIfPresent(key)
      if (maybe == null) Optional.empty() else Optional.of(maybe)
    }
    override def set(key: HitsCache.Key, values: Array[HitsCache.Value]): Unit = c.put(key, values)
  }

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
      scoreFunction,
      Optional.of(hitsCache)
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
