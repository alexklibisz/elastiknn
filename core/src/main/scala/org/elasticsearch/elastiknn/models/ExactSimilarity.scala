package org.elasticsearch.elastiknn.models

import org.elasticsearch.elastiknn.Similarity._
import org.elasticsearch.elastiknn.utils.{fastfor, sortedIntersectionCount}
import org.elasticsearch.elastiknn._

import scala.util.{Failure, Success, Try}

object ExactSimilarity {

  def jaccard(sbv1: SparseBoolVector, sbv2: SparseBoolVector): Try[(Double, Double)] =
    if (sbv1.totalIndices != sbv2.totalIndices)
      Failure(VectorDimensionException(sbv2.totalIndices, sbv1.totalIndices))
    else
      sortedIntersectionCount(sbv1.trueIndices, sbv2.trueIndices).map { isec =>
        val sim: Double = isec.toDouble / (sbv1.trueIndices.length + sbv2.trueIndices.length - isec)
        (sim, 1 - sim)
      }

  def hamming(sbv1: SparseBoolVector, sbv2: SparseBoolVector): Try[(Double, Double)] =
    if (sbv1.totalIndices != sbv2.totalIndices)
      Failure(VectorDimensionException(sbv2.totalIndices, sbv1.totalIndices))
    else
      sortedIntersectionCount(sbv1.trueIndices, sbv2.trueIndices).map { eqTrueCount =>
        val totalCount = sbv1.totalIndices
        val sbv1TrueCount = sbv1.trueIndices.length
        val sbv2TrueCount = sbv2.trueIndices.length
        val neqTrueCount = (sbv1TrueCount - eqTrueCount).max(0) + (sbv2TrueCount - eqTrueCount).max(0)
        val sim = (totalCount - neqTrueCount).toDouble / totalCount
        (sim, 1 - sim)
      }

  def l1(fv1: FloatVector, fv2: FloatVector): Try[(Double, Double)] =
    if (fv1.values.length != fv2.values.length)
      Failure(VectorDimensionException(fv2.values.length, fv1.values.length))
    else {
      var sumAbsDiff: Double = 0.0
      fastfor(0, _ < fv1.values.length) { i =>
        sumAbsDiff += (fv1.values(i) - fv2.values(i)).abs
      }
      Success(1.0 / sumAbsDiff.max(1e-6), sumAbsDiff)
    }

  def l2(fv1: FloatVector, fv2: FloatVector): Try[(Double, Double)] =
    if (fv1.values.length != fv2.values.length)
      Failure(VectorDimensionException(fv2.values.length, fv1.values.length))
    else {
      var sumSqrDiff: Double = 0.0
      fastfor(0, _ < fv1.values.length) { i =>
        sumSqrDiff += Math.pow(fv1.values(i) - fv2.values(i), 2)
      }
      val dist = Math.sqrt(sumSqrDiff)
      Success(1.0 / dist.max(1e-6), sumSqrDiff)
    }

  def angular(fv1: FloatVector, fv2: FloatVector): Try[(Double, Double)] =
    if (fv1.values.length != fv2.values.length)
      Failure(VectorDimensionException(fv2.values.length, fv1.values.length))
    else {
      var dotProd: Double = 0
      var fv1SqrSum: Double = 0
      var fv2SqrSum: Double = 0
      fastfor(0, _ < fv1.values.length) { i =>
        dotProd += fv1.values(i) * fv2.values(i)
        fv1SqrSum += math.pow(fv1.values(i), 2)
        fv2SqrSum += math.pow(fv2.values(i), 2)
      }
      val sim = dotProd / (math.sqrt(fv1SqrSum) * math.sqrt(fv2SqrSum))
      Success((1 + sim, 1 - sim))
    }

  def apply(similarity: Similarity, ekv1: ElastiKnnVector, ekv2: ElastiKnnVector): Try[(Double, Double)] = {
    import ElastiKnnVector.Vector.{SparseBoolVector, FloatVector}
    (similarity, ekv1.vector, ekv2.vector) match {
      case (SIMILARITY_JACCARD, SparseBoolVector(sbv1), SparseBoolVector(sbv2)) => jaccard(sbv1, sbv2)
      case (SIMILARITY_HAMMING, SparseBoolVector(sbv1), SparseBoolVector(sbv2)) => hamming(sbv1, sbv2)
      case (SIMILARITY_L1, FloatVector(fv1), FloatVector(fv2))                  => l1(fv1, fv2)
      case (SIMILARITY_L2, FloatVector(fv1), FloatVector(fv2))                  => l2(fv1, fv2)
      case (SIMILARITY_ANGULAR, FloatVector(fv1), FloatVector(fv2))             => angular(fv1, fv2)
      case _                                                                    => Failure(SimilarityAndTypeException(similarity, ekv1))
    }
  }

}
