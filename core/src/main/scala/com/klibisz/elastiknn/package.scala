package com.klibisz

package object elastiknn {

  final case class VectorDimensionException(actual: Int, expected: Int)
      extends IllegalArgumentException(s"Expected dimension $expected but got $actual")

  final case class ParseVectorException(msg: String = "Failed to parse vector", cause: Option[Throwable] = None)
      extends IllegalArgumentException(msg, cause.orNull)

  final case class SimilarityAndTypeException(similarity: Similarity, vector: ElastiKnnVector)
      extends IllegalArgumentException(s"Similarity ${similarity} is not compatible with vector ${vector}")

  def floatVectorPath(top: String): String = s"$top.floatVector.values"
  def boolVectorPath(top: String): String = s"$top.boolVector.values"

  private[elastiknn] lazy val ELASTIKNN_NAME = "elastiknn"
  private[elastiknn] lazy val ENDPOINT_PREFIX = s"_$ELASTIKNN_NAME"

  private[elastiknn] def illArgEx(msg: String, cause: Option[Throwable] = None): IllegalArgumentException =
    new IllegalArgumentException(msg, cause.orNull)

}
