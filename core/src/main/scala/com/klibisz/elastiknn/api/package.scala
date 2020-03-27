package com.klibisz.elastiknn

package object api {

  type JavaJsonMap = java.util.Map[String, AnyRef]

  sealed trait Similarity
  object Similarity {
    case object Jaccard extends Similarity
    case object Hamming extends Similarity
    case object L1 extends Similarity
    case object L2 extends Similarity
    case object Angular extends Similarity
  }

  sealed trait Vec
  object Vec {
    final case class SparseBool(trueIndices: Array[Int], totalIndices: Int) extends Vec {
      def sorted(): SparseBool = copy(trueIndices.sorted)
      override def equals(other: Any): Boolean = other match {
        case other: SparseBool => trueIndices.deep == other.trueIndices.deep && totalIndices == other.totalIndices
        case _                 => false
      }
    }
    final case class DenseFloat(values: Array[Float]) extends Vec {
      override def equals(other: Any): Boolean = other match {
        case other: DenseFloat => other.values.deep == values.deep
        case _                 => false
      }
    }
    final case class Indexed(index: String, id: String, field: String) extends Vec
  }

  sealed trait Mapping {
    def dims: Int
  }
  object Mapping {
    final case class SparseBool(dims: Int) extends Mapping
    final case class SparseIndexed(dims: Int) extends Mapping
    final case class DenseFloat(dims: Int) extends Mapping
    final case class JaccardLsh(dims: Int, bands: Int, rows: Int) extends Mapping
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
    final case class JaccardIndexed(field: String, vector: Vec) extends NearestNeighborsQuery {
      override def withVector(v: Vec): NearestNeighborsQuery = copy(vector = v)
      override def similarity: Similarity = Similarity.Jaccard
    }
    final case class HammingIndexed(field: String, vector: Vec) extends NearestNeighborsQuery {
      override def withVector(v: Vec): NearestNeighborsQuery = copy(vector = v)
      override def similarity: Similarity = Similarity.Hamming
    }
    final case class JaccardLsh(field: String, vector: Vec, candidates: Int, refine: Boolean) extends NearestNeighborsQuery {
      override def withVector(v: Vec): NearestNeighborsQuery = copy(vector = v)
      override def similarity: Similarity = Similarity.Jaccard
    }
  }
}
