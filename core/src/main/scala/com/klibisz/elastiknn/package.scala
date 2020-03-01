package com.klibisz

import com.klibisz.elastiknn.KNearestNeighborsQuery._

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
    implicit val ekv: QueryVectorLike[ElastiKnnVector] = (a: ElastiKnnVector) => QueryVector.Given(a)
    implicit val iqv: QueryVectorLike[IndexedQueryVector] = (a: IndexedQueryVector) => QueryVector.Indexed(a)
    implicit def ekvLike[A: ElastiKnnVectorLike]: QueryVectorLike[A] =
      (a: A) => QueryVector.Given(implicitly[ElastiKnnVectorLike[A]].apply(a))
  }

  trait QueryOptionsLike[T] {
    def apply(a: T): KNearestNeighborsQuery.QueryOptions
  }

  object QueryOptionsLike {
    implicit val id: QueryOptionsLike[QueryOptions] = identity
    implicit val exact: QueryOptionsLike[ExactComputedQueryOptions] = (a: ExactComputedQueryOptions) => QueryOptions.ExactComputed(a)
    implicit val lsh: QueryOptionsLike[LshQueryOptions] = (a: LshQueryOptions) => QueryOptions.Lsh(a)
  }

}
