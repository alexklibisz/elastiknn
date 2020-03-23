package com.klibisz.elastiknn.api

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

object Vector {

  private val circeConfig = Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withStrictDecoding

  case class SparseBoolVector(trueIndices: Vector[Int], totalIndices: Int)
  object SparseBoolVector {
    implicit val cfg: Configuration = circeConfig
    implicit val enc: Encoder[SparseBoolVector] = deriveConfiguredEncoder
    implicit val dec: Decoder[SparseBoolVector] = deriveConfiguredDecoder
  }

  case class DenseFloatVector(values: Vector[Float])
  object DenseFloatVector {
    implicit val cfg: Configuration = circeConfig
    implicit val enc: Encoder[DenseFloatVector] = deriveConfiguredEncoder
    implicit val dec: Decoder[DenseFloatVector] = deriveConfiguredDecoder
  }

}
