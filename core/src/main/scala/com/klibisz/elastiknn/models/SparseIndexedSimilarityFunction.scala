package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Similarity, Vec}

import scala.util.Try

sealed trait SparseIndexedSimilarityFunction extends ((Vec.SparseBool, Int, Int) => Try[ExactSimilarityScore]) {
  def similarity: Similarity
  override def apply(queryVec: Vec.SparseBool, intersection: Int, numTrue: Int): Try[ExactSimilarityScore]
}

object SparseIndexedSimilarityFunction {
  object Jaccard extends SparseIndexedSimilarityFunction {
    override def similarity: Similarity = Similarity.Jaccard
    override def apply(queryVec: Vec.SparseBool, intersection: Int, numTrue: Int): Try[ExactSimilarityScore] = Try {
      val sim = intersection * 1.0 / (queryVec.trueIndices.length + numTrue - intersection)
      ExactSimilarityScore(sim, 1 - sim)
    }
  }
  object Hamming extends SparseIndexedSimilarityFunction {
    override def similarity: Similarity = Similarity.Hamming
    override def apply(queryVec: Vec.SparseBool, intersection: Int, numTrue: Int): Try[ExactSimilarityScore] = Try {
      val eqTrue = intersection
      val total = queryVec.totalIndices
      val v1True = queryVec.trueIndices.length
      val v2True = numTrue
      val neqTrue = (v1True - intersection).max(0) + (v2True - eqTrue).max(0)
      val sim = (total - neqTrue).toDouble / total
      ExactSimilarityScore(sim, 1 - sim)
    }
  }
}
