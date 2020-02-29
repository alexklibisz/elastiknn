package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.utils.ArrayUtils._

import scala.util.{Failure, Try}

object ExactSimilarity {

  def jaccard(sbv1: SparseBoolVector, sbv2: SparseBoolVector): Try[(Double, Double)] =
    if (sbv1.totalIndices != sbv2.totalIndices)
      Failure(VectorDimensionException(sbv2.totalIndices, sbv1.totalIndices))
    else
      Try {
        val isec = sortedIntersectionCount(sbv1.trueIndices, sbv2.trueIndices)
        val sim: Double = isec.toDouble / (sbv1.trueIndices.length + sbv2.trueIndices.length - isec)
        (sim, 1 - sim)
      }

  def hamming(sbv1: SparseBoolVector, sbv2: SparseBoolVector): Try[(Double, Double)] =
    if (sbv1.totalIndices != sbv2.totalIndices)
      Failure(VectorDimensionException(sbv2.totalIndices, sbv1.totalIndices))
    else
      Try {
        val eqTrueCount = sortedIntersectionCount(sbv1.trueIndices, sbv2.trueIndices)
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
    else
      Try {
        var sumAbsDiff: Double = 0.0
        var i = 0
        while (i < fv1.values.length) {
          sumAbsDiff += (fv1.values(i) - fv2.values(i)).abs
          i += 1
        }
        (1.0 / sumAbsDiff.max(1e-6), sumAbsDiff)
      }

  def l2(fv1: FloatVector, fv2: FloatVector): Try[(Double, Double)] =
    if (fv1.values.length != fv2.values.length)
      Failure(VectorDimensionException(fv2.values.length, fv1.values.length))
    else
      Try {
        var sumSqrDiff: Double = 0.0
        var i = 0
        while (i < fv1.values.length) {
          sumSqrDiff += Math.pow(fv1.values(i) - fv2.values(i), 2)
          i += 1
        }
        val dist = Math.sqrt(sumSqrDiff)
        (1.0 / dist.max(1e-6), sumSqrDiff)
      }

  def angular(fv1: FloatVector, fv2: FloatVector): Try[(Double, Double)] =
    if (fv1.values.length != fv2.values.length)
      Failure(VectorDimensionException(fv2.values.length, fv1.values.length))
    else
      Try {
        var dotProd: Double = 0
        var fv1SqrSum: Double = 0
        var fv2SqrSum: Double = 0
        var i = 0
        while (i < fv1.values.length) {
          dotProd += fv1.values(i) * fv2.values(i)
          fv1SqrSum += math.pow(fv1.values(i), 2)
          fv2SqrSum += math.pow(fv2.values(i), 2)
          i += 1
        }
        val sim = dotProd / (math.sqrt(fv1SqrSum) * math.sqrt(fv2SqrSum))
        (1 + sim, 1 - sim)
      }

  def apply(similarity: Similarity, ekv1: ElastiKnnVector, ekv2: ElastiKnnVector): Try[(Double, Double)] = {
    import ElastiKnnVector.Vector.{FloatVector, SparseBoolVector}
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
