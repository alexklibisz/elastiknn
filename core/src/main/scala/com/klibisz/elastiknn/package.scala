package com.klibisz

import com.klibisz.elastiknn.KNearestNeighborsQuery._
import com.klibisz.elastiknn.ProcessorOptions.{ExactComputedModelOptions, ExactIndexedModelOptions, JaccardLshModelOptions, ModelOptions}

package object elastiknn {

  val ELASTIKNN_NAME: String = "elastiknn"
  val ENDPOINT_PREFIX: String = s"_$ELASTIKNN_NAME"

  final case class VectorDimensionException(actual: Int, expected: Int)
      extends IllegalArgumentException(s"Expected dimension $expected but got $actual")

  final case class EmptyVectorExecption() extends IllegalArgumentException(s"Vector cannot be empty")

  final case class ParseVectorException(msg: String = "Failed to parse vector", cause: Option[Throwable] = None)
      extends IllegalArgumentException(msg, cause.orNull)

  final case class SimilarityAndTypeException(similarity: Similarity, vector: ElastiKnnVector)
      extends IllegalArgumentException(s"Similarity ${similarity} is not compatible with vector ${vector}")

  private[elastiknn] def illArgEx(msg: String, cause: Option[Throwable] = None): IllegalArgumentException =
    new IllegalArgumentException(msg, cause.orNull)

  trait ElastiKnnVectorLike[A] {
    def apply(a: A): ElastiKnnVector
  }

  object ElastiKnnVectorLike {
    implicit val id: ElastiKnnVectorLike[ElastiKnnVector] = identity
    implicit val fv: ElastiKnnVectorLike[FloatVector] = (a: FloatVector) => ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(a))
    implicit val sbv: ElastiKnnVectorLike[SparseBoolVector] = (a: SparseBoolVector) =>
      ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(a))
  }

  trait QueryVectorLike[T] {
    def apply(a: T): QueryVector
  }

  object QueryVectorLike {
    implicit val id: QueryVectorLike[QueryVector] = identity
    implicit val ekv: QueryVectorLike[ElastiKnnVector] = QueryVector.Given(_)
    implicit val iqv: QueryVectorLike[IndexedQueryVector] = QueryVector.Indexed(_)
    implicit def ekvLike[A: ElastiKnnVectorLike]: QueryVectorLike[A] =
      (a: A) => QueryVector.Given(implicitly[ElastiKnnVectorLike[A]].apply(a))
  }

  trait ModelOptionsLike[T] {
    def apply(a: T): ProcessorOptions.ModelOptions
  }

  object ModelOptionsLike {
    implicit val id: ModelOptionsLike[ModelOptions] = identity
    implicit val excmp: ModelOptionsLike[ExactComputedModelOptions] = ModelOptions.ExactComputed(_)
    implicit val exix: ModelOptionsLike[ExactIndexedModelOptions] = ModelOptions.ExactIndexed(_)
    implicit val jacclsh: ModelOptionsLike[JaccardLshModelOptions] = ModelOptions.JaccardLsh(_)
  }

  trait QueryOptionsLike[T] {
    def apply(a: T): KNearestNeighborsQuery.QueryOptions
  }

  object QueryOptionsLike {
//    implicit def id[T <: QueryOptions](t: T): T = t
    implicit val id: QueryOptionsLike[QueryOptions] = identity
    implicit val excmp: QueryOptionsLike[ExactComputedQueryOptions] = (a: ExactComputedQueryOptions) => QueryOptions.ExactComputed(a)
    implicit val exix: QueryOptionsLike[ExactIndexedQueryOptions] = (a: ExactIndexedQueryOptions) => QueryOptions.ExactIndexed(a)
    implicit val jacclsh: QueryOptionsLike[JaccardLshQueryOptions] = (a: JaccardLshQueryOptions) => QueryOptions.JaccardLsh(a)
  }

}
