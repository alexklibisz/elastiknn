package com.klibisz

package object elastiknn {

  val ELASTIKNN_NAME: String = "elastiknn"

  final case class VectorDimensionException(actual: Int, expected: Int)
      extends IllegalArgumentException(s"Expected dimension $expected but got $actual")

}
