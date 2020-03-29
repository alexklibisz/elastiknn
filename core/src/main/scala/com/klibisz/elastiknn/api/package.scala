package com.klibisz.elastiknn

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

    final case class SparseBool(trueIndices: Array[Int], totalIndices: Int) extends Vec {
      def sorted(): SparseBool = copy(trueIndices.sorted)
      override def equals(other: Any): Boolean = other match {
        case other: SparseBool => trueIndices.deep == other.trueIndices.deep && totalIndices == other.totalIndices
        case _                 => false
      }
      override def toString: String = s"SparseBool(${trueIndices.take(3).mkString(",")},...,$totalIndices)"
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

    final case class DenseFloat(values: Array[Float]) extends Vec {
      override def equals(other: Any): Boolean = other match {
        case other: DenseFloat => other.values.deep == values.deep
        case _                 => false
      }
      override def toString: String = s"DenseFloat(${values.take(3).map(n => f"$n%.2f").mkString(",")},...,${values.length})"
    }

    object DenseFloat {
      def random(length: Int, multiple: Float = 3.14f)(implicit rng: Random): DenseFloat =
        DenseFloat((0 until length).toArray.map(_ => rng.nextFloat() * multiple))

      def randoms(length: Int, n: Int, multiple: Float = 3.14f)(implicit rng: Random): Vector[DenseFloat] =
        (0 until n).map(_ => random(length, multiple)).toVector
    }

    final case class Indexed(index: String, id: String, field: String) extends Vec
  }

  sealed trait Mapping {
    def dims: Int
  }
  object Mapping {
    final case class SparseBool(dims: Int) extends Mapping
    final case class SparseIndexed(dims: Int) extends Mapping
    final case class JaccardLsh(dims: Int, bands: Int, rows: Int) extends Mapping
    final case class HammingLsh(dims: Int, bands: Int, rows: Int) extends Mapping
    final case class DenseFloat(dims: Int) extends Mapping
  }

  sealed trait NearestNeighborsQuery {
    def field: String
    def vector: Vec
    def similarity: Similarity
    def withVector(v: Vec): NearestNeighborsQuery
  }
  object NearestNeighborsQuery {
    final case class Exact(field: String, vector: Vec, similarity: Similarity) extends NearestNeighborsQuery {
      override def withVector(v: Vec): NearestNeighborsQuery = copy(vector = v)
    }
    final case class SparseIndexed(field: String, vector: Vec, similarity: Similarity) extends NearestNeighborsQuery {
      override def withVector(v: Vec): NearestNeighborsQuery = copy(vector = v)
    }
    final case class JaccardLsh(field: String, vector: Vec, candidates: Int) extends NearestNeighborsQuery {
      override def withVector(v: Vec): NearestNeighborsQuery = copy(vector = v)
      override def similarity: Similarity = Similarity.Jaccard
    }
  }
}
