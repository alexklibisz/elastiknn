package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.VectorDimensionException
import com.klibisz.elastiknn.api.{Similarity, Vec}
import com.klibisz.elastiknn.utils.ArrayUtils.sortedIntersectionCount

import scala.util._

/**
  * You can always compute distance between two vectors, but similarity is not always well-defined.
  * For example, there is an intuitive notion of similarity in euclidean space, but no precise expression.
  * This case classes represents that notion by containing both the distance and a similarity or pseudo-similarity that
  * expresses the distance.
  * @param score Distance expressed as a similarity score where higher means more similar. This is guaranteed to be >= 0,
  *              so it can be used as an elasticsearch score.
  * @param distance The actual distance.
  */
final case class ExactSimilarityScore(score: Double, distance: Double)

sealed trait ExactSimilarityFunction[V <: Vec] extends ((V, V) => Try[ExactSimilarityScore]) {
  def similarity: Similarity
  override def equals(other: Any): Boolean = other match {
    case f: ExactSimilarityFunction[V] => f.similarity == similarity
    case _                             => false
  }
}

object ExactSimilarityFunction {
  object Jaccard extends ExactSimilarityFunction[Vec.SparseBool] {
    override def similarity: Similarity = Similarity.Jaccard
    override def apply(v1: Vec.SparseBool, v2: Vec.SparseBool): Try[ExactSimilarityScore] =
      if (v1.totalIndices != v2.totalIndices)
        Failure(VectorDimensionException(v2.totalIndices, v1.totalIndices))
      else
        Try {
          val isec = sortedIntersectionCount(v1.trueIndices, v2.trueIndices)
          val sim = isec * 1.0 / (v1.trueIndices.length + v2.trueIndices.length - isec)
          ExactSimilarityScore(sim, 1 - sim)
        }
  }

  object Hamming extends ExactSimilarityFunction[Vec.SparseBool] {
    override def similarity: Similarity = Similarity.Hamming
    override def apply(v1: Vec.SparseBool, v2: Vec.SparseBool): Try[ExactSimilarityScore] =
      if (v1.totalIndices != v2.totalIndices)
        Failure(VectorDimensionException(v2.totalIndices, v1.totalIndices))
      else
        Try {
          val eqTrueCount = sortedIntersectionCount(v1.trueIndices, v2.trueIndices)
          val totalCount = v1.totalIndices
          val v1TrueCount = v1.trueIndices.length
          val v2TrueCount = v2.trueIndices.length
          val neqTrueCount = (v1TrueCount - eqTrueCount).max(0) + (v2TrueCount - eqTrueCount).max(0)
          val sim = (totalCount - neqTrueCount).toDouble / totalCount
          ExactSimilarityScore(sim, 1 - sim)
        }
  }
  object L1 extends ExactSimilarityFunction[Vec.DenseFloat] {
    override def similarity: Similarity = Similarity.L1
    override def apply(v1: Vec.DenseFloat, v2: Vec.DenseFloat): Try[ExactSimilarityScore] =
      if (v1.values.length != v2.values.length)
        Failure(VectorDimensionException(v2.values.length, v1.values.length))
      else
        Try {
          var sumAbsDiff: Double = 0.0
          var i = 0
          while (i < v1.values.length) {
            sumAbsDiff += (v1.values(i) - v2.values(i)).abs
            i += 1
          }
          ExactSimilarityScore(1.0 / sumAbsDiff.max(1e-6), sumAbsDiff)
        }
  }
  object L2 extends ExactSimilarityFunction[Vec.DenseFloat] {
    override def similarity: Similarity = Similarity.L2
    override def apply(v1: Vec.DenseFloat, v2: Vec.DenseFloat): Try[ExactSimilarityScore] =
      if (v1.values.length != v2.values.length)
        Failure(VectorDimensionException(v2.values.length, v1.values.length))
      else
        Try {
          var sumSqrDiff: Double = 0.0
          var i = 0
          while (i < v1.values.length) {
            sumSqrDiff += Math.pow(v1.values(i) - v2.values(i), 2)
            i += 1
          }
          val dist = Math.sqrt(sumSqrDiff)
          ExactSimilarityScore(1.0 / dist.max(1e-6), sumSqrDiff)
        }
  }
  object Angular extends ExactSimilarityFunction[Vec.DenseFloat] {
    override def similarity: Similarity = Similarity.Angular
    override def apply(v1: Vec.DenseFloat, v2: Vec.DenseFloat): Try[ExactSimilarityScore] =
      if (v1.values.length != v2.values.length)
        Failure(VectorDimensionException(v2.values.length, v1.values.length))
      else
        Try {
          var dotProd: Double = 0
          var v1SqrSum: Double = 0
          var v2SqrSum: Double = 0
          var i = 0
          while (i < v1.values.length) {
            dotProd += v1.values(i) * v2.values(i)
            v1SqrSum += math.pow(v1.values(i), 2)
            v2SqrSum += math.pow(v2.values(i), 2)
            i += 1
          }
          val sim = dotProd / (math.sqrt(v1SqrSum) * math.sqrt(v2SqrSum))
          ExactSimilarityScore(1 + sim, 1 - sim)
        }
  }
}
