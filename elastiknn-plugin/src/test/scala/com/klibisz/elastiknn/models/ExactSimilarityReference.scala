package com.klibisz.elastiknn.models

import breeze.linalg.{DenseVector, euclideanDistance}
import com.klibisz.elastiknn.api.Vec
import breeze.linalg._

/** Simpler but slower implementations of exact similarities.
  */
object ExactSimilarityReference {

  val L2: (Vec.DenseFloat, Vec.DenseFloat) => Double = (v1: Vec.DenseFloat, v2: Vec.DenseFloat) => {
    1 / (1 + euclideanDistance(new DenseVector(v1.values), new DenseVector(v2.values)))
  }

  val L1: (Vec.DenseFloat, Vec.DenseFloat) => Double = (v1: Vec.DenseFloat, v2: Vec.DenseFloat) => {
    1 / (1 + manhattanDistance(new DenseVector(v1.values), new DenseVector(v2.values)))
  }

  val Cosine: (Vec.DenseFloat, Vec.DenseFloat) => Double = (v1: Vec.DenseFloat, v2: Vec.DenseFloat) => {
    1 + (1 - cosineDistance(new DenseVector(v1.values.map(_.toDouble)), new DenseVector(v2.values.map(_.toDouble))))
  }
  val Dot: (Vec.DenseFloat, Vec.DenseFloat) => Double = (v1: Vec.DenseFloat, v2: Vec.DenseFloat) => {
    1 + (1 - dotDistance(new DenseVector(v1.values.map(_.toDouble)), new DenseVector(v2.values.map(_.toDouble))))
  }
  val Hamming: (Vec.SparseBool, Vec.SparseBool) => Double = (v1: Vec.SparseBool, v2: Vec.SparseBool) => {
    val d1 = new Array[Boolean](v1.totalIndices)
    val d2 = new Array[Boolean](v2.totalIndices)
    v1.trueIndices.foreach(i => d1.update(i, true))
    v2.trueIndices.foreach(i => d2.update(i, true))
    d1.zip(d2).count { case (a, b) => a == b } * 1d / d1.length
  }

  val Jaccard: (Vec.SparseBool, Vec.SparseBool) => Double = (v1: Vec.SparseBool, v2: Vec.SparseBool) => {
    val isec = v1.trueIndices.toIndexedSeq.intersect(v2.trueIndices.toIndexedSeq).length
    val denom = v1.trueIndices.length + v2.trueIndices.length - isec
    if (isec == 0 && denom == 0) 1d
    else if (denom > 0) isec * 1d / denom
    else 0d
  }

}
