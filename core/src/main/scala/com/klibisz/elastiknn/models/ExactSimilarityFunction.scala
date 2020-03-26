package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.VectorDimensionException
import com.klibisz.elastiknn.api.{Similarity, Vec}
import com.klibisz.elastiknn.utils.ArrayUtils.sortedIntersectionCount

import scala.util.{Failure, Try}

/**
  * You can always compute distance between two points, but similarity is not always well defined.
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
          val sim: Float = isec / (v1.trueIndices.length + v2.trueIndices.length - isec)
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
}
