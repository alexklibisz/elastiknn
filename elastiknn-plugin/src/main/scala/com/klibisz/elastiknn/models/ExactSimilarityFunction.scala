package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.vectors.FloatVectorOps

sealed trait ExactSimilarityFunction[V <: Vec, S <: StoredVec] extends ((V, S) => Double) {
  def maxScore: Float
}

object ExactSimilarityFunction {
  object Jaccard extends ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] {
    override def maxScore: Float = 1f
    override def apply(v1: Vec.SparseBool, v2: StoredVec.SparseBool): Double =
      ExactModel.jaccardSimilarity(v1.trueIndices, v2.trueIndices, v1.totalIndices)
  }
  object Hamming extends ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] {
    override def maxScore: Float = 1f
    override def apply(v1: Vec.SparseBool, v2: StoredVec.SparseBool): Double =
      ExactModel.hammingSimilarity(v1.trueIndices, v2.trueIndices, v1.totalIndices)
  }
  final class L1(floatVectorOps: FloatVectorOps) extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    override def maxScore: Float = 1f
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double =
      ExactModel.l1Similarity(floatVectorOps, v1.values, v2.values)
  }
  final class L2(floatVectorOps: FloatVectorOps) extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    override def maxScore: Float = 1f
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double =
      ExactModel.l2Similarity(floatVectorOps, v1.values, v2.values)
  }
  final class Cosine(floatVectorOps: FloatVectorOps) extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    override def maxScore: Float = 2f
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double =
      ExactModel.cosineSimilarity(floatVectorOps, v1.values, v2.values)
  }
}
