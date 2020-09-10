package com.klibisz.elastiknn

import scala.annotation.tailrec
import scala.util.Random

package object api {

  type JavaJsonMap = java.util.Map[String, Object]
  type ScalaJsonMap = Map[String, AnyRef]

  sealed trait Similarity
  object Similarity {
    case object Jaccard extends Similarity
    case object Hamming extends Similarity
    case object L1 extends Similarity
    case object L2 extends Similarity
    case object Angular extends Similarity
    val values: Seq[Similarity] = Seq(Jaccard, Hamming, L1, L2, Angular)
  }

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
        case other: SparseBool => trueIndices.deep == other.trueIndices.deep && totalIndices == other.totalIndices
        case _                 => false
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
        case other: DenseFloat => other.values.deep == values.deep
        case _                 => false
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
        val v = DenseFloat((0 until length).toArray.map(_ => rng.nextGaussian.toFloat * scale))
        if (unit) {
          val norm = math.sqrt(v.values.map(x => x * x).sum).toFloat
          DenseFloat(v.values.map(_ / norm))
        } else v
      }

      def randoms(length: Int, n: Int, unit: Boolean = false, scale: Int = 1)(implicit rng: Random): Vector[DenseFloat] =
        (0 until n).map(_ => random(length, unit, scale)).toVector
    }

    final case class Indexed(index: String, id: String, field: String) extends Vec

    final case class Empty() extends Vec

  }

  sealed trait Mapping {
    def dims: Int
  }
  object Mapping {
    final case class SparseBool(dims: Int) extends Mapping
    final case class SparseIndexed(dims: Int) extends Mapping
    final case class JaccardLsh(dims: Int, L: Int, k: Int) extends Mapping
    final case class HammingLsh(dims: Int, L: Int, k: Int) extends Mapping
    final case class DenseFloat(dims: Int) extends Mapping
    final case class AngularLsh(dims: Int, L: Int, k: Int) extends Mapping
    final case class L2Lsh(dims: Int, L: Int, k: Int, w: Int) extends Mapping
    final case class PermutationLsh(dims: Int, k: Int, repeating: Boolean) extends Mapping
  }

  sealed trait NearestNeighborsQuery {
    def field: String
    def vec: Vec
    def similarity: Similarity
    def withVec(v: Vec): NearestNeighborsQuery
  }
  object NearestNeighborsQuery {
    final case class Exact(field: String, similarity: Similarity, vec: Vec = Vec.Empty()) extends NearestNeighborsQuery {
      override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)
    }

    final case class SparseIndexed(field: String, similarity: Similarity, vec: Vec = Vec.Empty()) extends NearestNeighborsQuery {
      override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)
    }

    sealed trait ApproximateQuery extends NearestNeighborsQuery {
      def candidates: Int
    }

    final case class JaccardLsh(field: String, candidates: Int, vec: Vec = Vec.Empty()) extends ApproximateQuery {
      override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)
      override def similarity: Similarity = Similarity.Jaccard
    }

    final case class HammingLsh(field: String, candidates: Int, vec: Vec = Vec.Empty()) extends ApproximateQuery {
      override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)
      override def similarity: Similarity = Similarity.Hamming
    }

    final case class AngularLsh(field: String, candidates: Int, vec: Vec = Vec.Empty()) extends ApproximateQuery {
      override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)
      override def similarity: Similarity = Similarity.Angular
    }

    final case class L2Lsh(field: String, candidates: Int, probes: Int = 0, vec: Vec = Vec.Empty()) extends ApproximateQuery {
      override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)
      override def similarity: Similarity = Similarity.L2
    }

    final case class PermutationLsh(field: String, similarity: Similarity, candidates: Int, vec: Vec = Vec.Empty())
        extends ApproximateQuery {
      override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)
    }

  }
}
