package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.ExactModel.Cosine
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.vectors.FloatVectorOps

sealed trait ExactSimilarityFunction[V <: Vec, S <: StoredVec] extends ((V, S) => Double) {
  def maxScore: Float
}

object ExactSimilarityFunction {
  object Jaccard extends ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] {
    private val m = new ExactModel.Jaccard
    override def maxScore: Float = 1f
    override def apply(v1: Vec.SparseBool, v2: StoredVec.SparseBool): Double = m.similarity(v1.trueIndices, v2.trueIndices, v1.totalIndices)
  }
  object Hamming extends ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] {
    private val m = new ExactModel.Hamming
    override def maxScore: Float = 1f
    override def apply(v1: Vec.SparseBool, v2: StoredVec.SparseBool): Double = m.similarity(v1.trueIndices, v2.trueIndices, v1.totalIndices)
  }
  object L1 extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    private val m = new ExactModel.L1
    override def maxScore: Float = 1f
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double = m.similarity(v1.values, v2.values)
  }
  final class L2(floatVectorOps: FloatVectorOps) extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    override def maxScore: Float = 1f
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double = {
      val dist = floatVectorOps.euclideanDistance(v1.values, v2.values)
      1.0 / (1 + dist)
    }
  }
  object Cosine extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    private val m = new Cosine
    override def maxScore: Float = 2f
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double = m.similarity(v1.values, v2.values);
  }
}
