package com.klibisz.elastiknn

package object api {

  sealed trait Similarity
  object Similarity {
    case object Jaccard extends Similarity
    case object Hamming extends Similarity
    case object L1 extends Similarity
    case object L2 extends Similarity
    case object Angular extends Similarity
  }

  sealed trait SparseBoolVectorModelOptions
  object SparseBoolVectorModelOptions {
    case object JaccardIndexed extends SparseBoolVectorModelOptions
    final case class JaccardLsh(bands: Int, rows: Int) extends SparseBoolVectorModelOptions
  }

  sealed trait DenseFloatVectorModelOptions
  object DenseFloatVectorModelOptions {
    final case class AngularLsh() extends DenseFloatVectorModelOptions
  }

  sealed trait Mapping
  object Mapping {
    final case class SparseBool(dims: Int, modelOptions: Option[SparseBoolVectorModelOptions]) extends Mapping
    final case class DenseFloat(dims: Int, modelOptions: Option[DenseFloatVectorModelOptions]) extends Mapping
  }

  sealed trait Vec
  object Vec {
    final case class SparseBool(trueIndices: Array[Int], totalIndices: Int) extends Vec {
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

  sealed trait QueryOptions
  object QueryOptions {
    final case class Exact(similarity: Similarity) extends QueryOptions
    case object JaccardIndexed extends QueryOptions
    case class JaccardLsh(candidates: Int, refine: Boolean) extends QueryOptions
  }

  sealed trait Query
  object Query {
    final case class NearestNeighborsQuery(index: String, field: String, vector: Vec, queryOptions: QueryOptions) extends Query
    final case class RadiusQuery(index: String, field: String, vector: Vec, queryOptions: QueryOptions, radius: Float) extends Query
  }
}
