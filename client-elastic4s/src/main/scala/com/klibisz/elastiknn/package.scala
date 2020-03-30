package com.klibisz

package object elastiknn {

  val ELASTIKNN_NAME: String = "elastiknn"

  final case class VectorDimensionException(actual: Int, expected: Int)
      extends IllegalArgumentException(s"Expected dimension $expected but got $actual")

  final case class EmptyVectorExecption() extends IllegalArgumentException(s"Vector cannot be empty")

  final case class ParseVectorException(msg: String = "Failed to parse vector", cause: Option[Throwable] = None)
      extends IllegalArgumentException(msg, cause.orNull)

  private[elastiknn] def illArgEx(msg: String, cause: Option[Throwable] = None): IllegalArgumentException =
    new IllegalArgumentException(msg, cause.orNull)

}
