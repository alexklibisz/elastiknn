package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Similarity, Vec}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.utils.ArrayUtils._

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

sealed trait ExactSimilarityFunction[V <: Vec, S <: StoredVec] extends ((V, S) => Double) {
  def similarity: Similarity
  override def equals(other: Any): Boolean = other match {
    case f: ExactSimilarityFunction[V, S] => f.similarity == similarity
    case _                                => false
  }
}

object ExactSimilarityFunction {

  object Jaccard extends ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] {
    override def similarity: Similarity = Similarity.Jaccard
    override def apply(v1: Vec.SparseBool, v2: StoredVec.SparseBool): Double = {
      val isec = sortedIntersectionCount(v1.trueIndices, v2.trueIndices)
      val sim = isec * 1.0 / (v1.trueIndices.length + v2.trueIndices.length - isec)
      sim
    }
  }

  object Hamming extends ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] {
    override def similarity: Similarity = Similarity.Hamming
    override def apply(v1: Vec.SparseBool, v2: StoredVec.SparseBool): Double = {
      val eqTrueCount = sortedIntersectionCount(v1.trueIndices, v2.trueIndices)
      val totalCount = v1.totalIndices
      val v1TrueCount = v1.trueIndices.length
      val v2TrueCount = v2.trueIndices.length
      val neqTrueCount = (v1TrueCount - eqTrueCount).max(0) + (v2TrueCount - eqTrueCount).max(0)
      val sim = (totalCount - neqTrueCount).toDouble / totalCount
      sim
    }
  }
  object L1 extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    override def similarity: Similarity = Similarity.L1
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double = {
      var sumAbsDiff: Double = 0.0
      var i = 0
      while (i < v1.values.length) {
        sumAbsDiff += (v1.values(i) - v2.values(i)).abs
        i += 1
      }
      1.0 / sumAbsDiff.max(1e-6)
    }
  }
  object L2 extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    override def similarity: Similarity = Similarity.L2
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double = {
      var sumSqrDiff: Double = 0.0
      var i = 0
      while (i < v1.values.length) {
        val diff = v1.values(i) - v2.values(i)
        sumSqrDiff += math.pow(diff, 2)
        i += 1
      }
      val dist = math.sqrt(sumSqrDiff)
      1.0 / dist.max(1e-6)
    }
  }
  object Angular extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    override def similarity: Similarity = Similarity.Angular
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double = {
      var dotProd: Double = 0
      var v1SqrSum: Double = 1e-16 // Prevent NaNs.
      var v2SqrSum: Double = 1e-16
      var i = 0
      while (i < v1.values.length) {
        dotProd += v1.values(i) * v2.values(i)
        v1SqrSum += math.pow(v1.values(i), 2)
        v2SqrSum += math.pow(v2.values(i), 2)
        i += 1
      }
      val sim = dotProd / (math.sqrt(v1SqrSum) * math.sqrt(v2SqrSum))
      1 + sim
    }
  }
}
