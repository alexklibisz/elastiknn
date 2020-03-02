package com.klibisz.elastiknn.query

import java.lang
import java.util.Objects

import com.google.common.cache._
import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper
import com.klibisz.elastiknn.models.ExactSimilarity
import com.klibisz.elastiknn._
import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.Explanation
import org.elasticsearch.common.lucene.search.function._

/**
  * Query function which exact similarity scores for indexed vectors against the given query vector.
  * @param similarity The similarity function to use.
  * @param fieldData Object providing access to stored ElastiKnnVectors.
  * @param queryVector The query vector.
  * @param useCache Use a cache keyed on leaf reader context and doc id to avoid re-parsing vectors.
  * @param numCandidates If given, maintain a heap of subquery scores of this size, and only compute the exact score
  *                      if the subquery score is greater than the min in the heap.
  */
class KnnExactScoreFunction(val similarity: Similarity,
                            val queryVector: ElastiKnnVector,
                            val fieldData: ElastiKnnVectorFieldMapper.FieldData,
                            val useCache: Boolean,
                            val numCandidates: Option[Int])
    extends ScoreFunction(CombineFunction.REPLACE) {

  import KnnExactScoreFunction.vectorCache

  // Combine the numCandidates option with a corresponding heap.
  private val candidatesHeapOpt: Option[(Int, MinMaxPriorityQueue[lang.Float])] =
    numCandidates.map(n => (n, MinMaxPriorityQueue.create[java.lang.Float]()))

  override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
    // This .load call is expensive so it's important to only instantiate once.
    lazy val atomicFieldData = fieldData.load(ctx)

    // val logger: Logger = LogManager.getLogger(s"${this.getClass} - ${ctx.docBase} - ${ctx.ord}")

    new LeafScoreFunction {

      override def score(docId: Int, subQueryScore: Float): Double = {

        val computeExact: Boolean = candidatesHeapOpt match {
          case Some((n, heap)) =>
            if (heap.size() < n) {
              heap.add(subQueryScore)
              true
            } else if (subQueryScore > heap.peekFirst()) {
              heap.removeFirst()
              heap.add(subQueryScore)
              true
            } else false
          case None => true
        }

        if (computeExact) {
          val storedVector: ElastiKnnVector = if (useCache) {
            vectorCache.get((ctx, docId), () => atomicFieldData.getElastiKnnVector(docId).get)
          } else atomicFieldData.getElastiKnnVector(docId).get
          val (sim, _) = ExactSimilarity(similarity, queryVector, storedVector).get
          sim
        } else 0
      }

      override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
        Explanation.`match`(100,
                            "The KnnExactScoreFunction computes exact similarity scores for indexed vectors against a given query vector.")
    }
  }

  override def needsScores(): Boolean = false

  override def doEquals(other: ScoreFunction): Boolean = other match {
    case that: KnnExactScoreFunction =>
      this.similarity == that.similarity &&
        this.queryVector == that.queryVector &&
        this.fieldData == that.fieldData &&
        this.numCandidates == that.numCandidates &&
        this.useCache == that.useCache
    case _ => false
  }

  override def doHashCode(): Int = Objects.hash(similarity, queryVector, fieldData, numCandidates, useCache.asInstanceOf[AnyRef])
}

object KnnExactScoreFunction {

  /** Cache keyed on leaf reader context and docId. */
  private val vectorCache: Cache[(LeafReaderContext, Integer), ElastiKnnVector] =
    CacheBuilder.newBuilder.softValues.build[(LeafReaderContext, Integer), ElastiKnnVector]()
}
