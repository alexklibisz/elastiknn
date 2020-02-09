package com.klibisz.elastiknn.query

import java.util.Objects

import com.google.common.cache.{Cache, CacheBuilder}
import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper
import com.klibisz.elastiknn.models.ExactSimilarity
import com.klibisz.elastiknn.{ElastiKnnVector, Similarity}
import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.Explanation
import org.elasticsearch.common.lucene.search.function.{CombineFunction, LeafScoreFunction, ScoreFunction}

/**
  * Query function which exact similarity scores for indexed vectors against the given query vector.
  * @param similarity The similarity function to use.
  * @param fieldData Object providing access to stored ElastiKnnVectors.
  * @param queryVector The query vector.
  * @param useInMemoryCache Use a cache keyed on leaf reader context and doc id to avoid re-parsing vectors.
  */
case class KnnExactScoreFunction(similarity: Similarity,
                                 queryVector: ElastiKnnVector,
                                 fieldData: ElastiKnnVectorFieldMapper.FieldData,
                                 useInMemoryCache: Boolean)
    extends ScoreFunction(CombineFunction.REPLACE) {

  import KnnExactScoreFunction.vectorCache

  override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
    new LeafScoreFunction {
      override def score(docId: Int, subQueryScore: Float): Double = {
        lazy val storedVector = if (useInMemoryCache) {
          vectorCache.get((ctx, docId), () => fieldData.load(ctx).getElastiKnnVector(docId).get)
        } else fieldData.load(ctx).getElastiKnnVector(docId).get
        val (sim, _) = ExactSimilarity(similarity, queryVector, storedVector).get
        sim
      }

      override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
        Explanation.`match`(100,
                            "The KnnExactScoreFunction computes exact similarity scores for indexed vectors against a given query vector.")
    }
  }

  override def needsScores(): Boolean = false

  override def doEquals(other: ScoreFunction): Boolean = other match {
    case kesf: KnnExactScoreFunction => this == kesf
    case _                           => false
  }

  override def doHashCode(): Int = this.hashCode()
}

object KnnExactScoreFunction {

  /** Cache keyed on leaf reader context and docId. */
  private val vectorCache: Cache[(LeafReaderContext, Integer), ElastiKnnVector] =
    CacheBuilder.newBuilder.softValues.build[(LeafReaderContext, Integer), ElastiKnnVector]()
}
