package com.klibisz.elastiknn

package object api {

  sealed trait SparseBoolVectorModelOptions
  object SparseBoolVectorModelOptions {
    object Exact extends SparseBoolVectorModelOptions
    object JaccardIndexed extends SparseBoolVectorModelOptions
    case class JaccardLsh(bands: Int, rows: Int) extends SparseBoolVectorModelOptions
  }

  sealed trait DenseFloatVectorModelOptions
  object DenseFloatVectorModelOptions {
    object Exact extends DenseFloatVectorModelOptions
  }

  sealed trait Mapping

  object Mapping {
    case class SparseBoolVector(dims: Int, modelOptions: SparseBoolVectorModelOptions) extends Mapping
    case class DenseFloatVector(dims: Int, modelOptions: DenseFloatVectorModelOptions) extends Mapping
  }

  sealed trait Vector
  object Vector {
    case class SparseBoolVector(trueIndices: Array[Int], totalIndices: Int) extends Vector
    case class DenseFloatVector(values: Array[Float]) extends Vector
    case class IndexedVector(index: String, id: String, field: String) extends Vector
  }

  object Query {
    case class NearestNeighborsQuery(index: String, field: String, vector: Vector)
  }
}
