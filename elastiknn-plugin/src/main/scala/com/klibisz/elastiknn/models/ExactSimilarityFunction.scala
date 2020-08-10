package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.storage.StoredVec

sealed trait ExactSimilarityFunction[V <: Vec, S <: StoredVec] extends ((V, S) => Double)

object ExactSimilarityFunction {
  object Jaccard extends ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] {
    private val m = new ExactModel.Jaccard
    override def apply(v1: Vec.SparseBool, v2: StoredVec.SparseBool): Double = m.similarity(v1.trueIndices, v2.trueIndices, v1.totalIndices)
  }
  object Hamming extends ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] {
    private val m = new ExactModel.Hamming
    override def apply(v1: Vec.SparseBool, v2: StoredVec.SparseBool): Double = m.similarity(v1.trueIndices, v2.trueIndices, v1.totalIndices)
  }
  object L1 extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    private val m = new ExactModel.L1
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double = m.similarity(v1.values, v2.values)
  }
  object L2 extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    private val m = new ExactModel.L2
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double = m.similarity(v1.values, v2.values)
  }
  object Angular extends ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] {
    private val m = new ExactModel.Angular
    override def apply(v1: Vec.DenseFloat, v2: StoredVec.DenseFloat): Double = m.similarity(v1.values, v2.values);
  }
}
