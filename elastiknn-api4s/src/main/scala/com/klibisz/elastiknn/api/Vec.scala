package com.klibisz.elastiknn.api

import scala.annotation.tailrec
import scala.util.Random

sealed trait Vec

object Vec {

  sealed trait KnownDims {
    this: Vec =>
    def dims: Int
  }

  final case class SparseBool(trueIndices: Array[Int], totalIndices: Int) extends Vec with KnownDims {
    def sorted(): SparseBool = copy(trueIndices.sorted)

    def isSorted: Boolean = {
      @tailrec
      def check(i: Int): Boolean =
        if (i == trueIndices.length) true
        else if (trueIndices(i) < trueIndices(i - 1)) false
        else check(i + 1)

      check(1)
    }

    override def equals(other: Any): Boolean = other match {
      case other: SparseBool => (trueIndices sameElements other.trueIndices) && totalIndices == other.totalIndices
      case _ => false
    }

    override def toString: String = s"SparseBool(${trueIndices.take(3).mkString(",")},...,${trueIndices.length}/$totalIndices)"

    def dims: Int = totalIndices
  }

  object SparseBool {

    def random(totalIndices: Int, bias: Double = 0.5)(implicit rng: Random): SparseBool = {
      var trueIndices = Array.empty[Int]
      (0 until totalIndices).foreach(i => if (rng.nextDouble() <= bias) trueIndices :+= i else ())
      SparseBool(trueIndices, totalIndices)
    }

    def randoms(totalIndices: Int, n: Int, bias: Double = 0.5)(implicit rng: Random): Vector[SparseBool] =
      (0 until n).map(_ => random(totalIndices, bias)).toVector
  }

  final case class DenseFloat(values: Array[Float]) extends Vec with KnownDims {
    override def equals(other: Any): Boolean = other match {
      case other: DenseFloat => other.values sameElements values
      case _ => false
    }

    override def toString: String = s"DenseFloat(${values.take(3).map(n => f"$n%.2f").mkString(",")},...,${values.length})"

    def dot(other: DenseFloat): Float = {
      var (i, dp) = (0, 0f)
      while (i < other.values.length) {
        dp += (other.values(i) * values(i))
        i += 1
      }
      dp
    }

    override def dims: Int = values.length
  }

  object DenseFloat {
    def apply(values: Float*): DenseFloat = DenseFloat(values.toArray)

    def random(length: Int, unit: Boolean = false, scale: Int = 1)(implicit rng: Random): DenseFloat = {
      val v = DenseFloat((0 until length).toArray.map(_ => rng.nextGaussian().toFloat * scale))
      if (unit) {
        val norm = math.sqrt(v.values.map(x => x * x).sum.toDouble).toFloat
        DenseFloat(v.values.map(_ / norm))
      } else v
    }

    def randoms(length: Int, n: Int, unit: Boolean = false, scale: Int = 1)(implicit rng: Random): Vector[DenseFloat] =
      (0 until n).map(_ => random(length, unit, scale)).toVector
  }

  final case class Indexed(index: String, id: String, field: String) extends Vec

  final case class Empty() extends Vec

}
