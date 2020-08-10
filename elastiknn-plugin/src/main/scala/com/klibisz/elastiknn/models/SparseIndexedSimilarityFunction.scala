package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Similarity, Vec}

sealed trait SparseIndexedSimilarityFunction extends ((Vec.SparseBool, Int, Int) => Double) {
  def similarity: Similarity
  override def apply(queryVec: Vec.SparseBool, intersection: Int, numTrue: Int): Double
}

object SparseIndexedSimilarityFunction {
  object Jaccard extends SparseIndexedSimilarityFunction {
    override def similarity: Similarity = Similarity.Jaccard
    override def apply(queryVec: Vec.SparseBool, intersection: Int, numTrue: Int): Double = {
      val sim = intersection * 1.0 / (queryVec.trueIndices.length + numTrue - intersection)
      sim
    }
  }
  object Hamming extends SparseIndexedSimilarityFunction {
    override def similarity: Similarity = Similarity.Hamming
    override def apply(queryVec: Vec.SparseBool, intersection: Int, numTrue: Int): Double = {
      val eqTrue = intersection
      val total = queryVec.totalIndices
      val v1True = queryVec.trueIndices.length
      val v2True = numTrue
      val neqTrue = (v1True - intersection).max(0) + (v2True - eqTrue).max(0)
      val sim = (total - neqTrue).toDouble / total
      sim
    }
  }
}
