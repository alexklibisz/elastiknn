package com.klibisz.elastiknn

object ElastiknnException {

  class ElastiknnRuntimeException(msg: String, cause: Throwable = None.orNull) extends RuntimeException(msg, cause)

  class ElastiknnUnsupportedOperationException(msg: String, cause: Throwable = None.orNull) extends IllegalArgumentException(msg, cause)

  class ElastiknnIllegalArgumentException(msg: String, cause: Throwable = None.orNull) extends IllegalArgumentException(msg, cause)

  def vectorDimensions(actual: Int, expected: Int): ElastiknnIllegalArgumentException =
    new ElastiknnIllegalArgumentException(s"Expected dimension $expected but got $actual")
}
