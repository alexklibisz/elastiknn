package org.elasticsearch.elastiknn.models

import org.elasticsearch.elastiknn.Similarity._
import org.elasticsearch.elastiknn._
import org.elasticsearch.elastiknn.utils.Implicits._

import scala.util.{Failure, Success, Try}

object ExactSimilarity {

  def jaccard(sbv1: SparseBoolVector, sbv2: SparseBoolVector): Try[(Double, Double)] =
    if (sbv1.totalIndices != sbv2.totalIndices)
      Failure(VectorDimensionException(sbv2.totalIndices, sbv1.totalIndices))
    else {
      val isec: Int = IndexedSeqImplicits(sbv1.trueIndices).sortedIntersectionCount(sbv2.trueIndices)
      val sim: Double = isec.toDouble / (sbv1.trueIndices.length + sbv2.trueIndices.length - isec)
      Success((sim, sim))
    }

  def hamming(sbv1: SparseBoolVector, sbv2: SparseBoolVector): Try[(Double, Double)] = ???

  def l1(fv1: FloatVector, fv2: FloatVector): Try[(Double, Double)] = ???

  def l2(fv1: FloatVector, fv2: FloatVector): Try[(Double, Double)] = ???

  def angular(fv1: FloatVector, fv2: FloatVector): Try[(Double, Double)] = ???

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
