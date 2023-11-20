package com.klibisz.elastiknn.api

sealed trait Mapping {
  def dims: Int
}

object Mapping {
  final case class SparseBool(dims: Int) extends Mapping

  final case class JaccardLsh(dims: Int, L: Int, k: Int) extends Mapping

  final case class HammingLsh(dims: Int, L: Int, k: Int) extends Mapping

  final case class DenseFloat(dims: Int) extends Mapping

  final case class CosineLsh(dims: Int, L: Int, k: Int) extends Mapping

  final case class L2Lsh(dims: Int, L: Int, k: Int, w: Int) extends Mapping

  final case class PermutationLsh(dims: Int, k: Int, repeating: Boolean) extends Mapping
}
