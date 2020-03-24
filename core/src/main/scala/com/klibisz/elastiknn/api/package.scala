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
    case class JaccardLsh(bands: Int, rows: Int) extends SparseBoolVectorModelOptions
  }

  sealed trait DenseFloatVectorModelOptions
  object DenseFloatVectorModelOptions {
    case class AngularLsh() extends DenseFloatVectorModelOptions
  }

  sealed trait Mapping
  object Mapping {
    case class SparseBoolVector(dims: Int, modelOptions: Option[SparseBoolVectorModelOptions]) extends Mapping
    case class DenseFloatVector(dims: Int, modelOptions: Option[DenseFloatVectorModelOptions]) extends Mapping
  }

  sealed trait Vector
  object Vector {
    case class SparseBoolVector(trueIndices: Array[Int], totalIndices: Int) extends Vector
    case class DenseFloatVector(values: Array[Float]) extends Vector
    case class IndexedVector(index: String, id: String, field: String) extends Vector
  }

  sealed trait QueryOptions
  object QueryOptions {
    case class Exact(similarity: Similarity) extends QueryOptions
    case object JaccardIndexed extends QueryOptions
    case object JaccardLsh extends QueryOptions
  }

  sealed trait Query
  object Query {
    case class NearestNeighborsQuery(index: String, field: String, vector: Vector, queryOptions: QueryOptions) extends Query
    case class RadiusQuery(index: String, field: String, vector: Vector, queryOptions: QueryOptions, radius: Float) extends Query
  }
}
