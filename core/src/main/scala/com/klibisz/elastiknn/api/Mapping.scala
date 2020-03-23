package com.klibisz.elastiknn.api

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}

sealed trait SparseBoolVectorModelOptions
object SparseBoolVectorModelOptions {
  object Exact extends SparseBoolVectorModelOptions
  object JaccardIndexed extends SparseBoolVectorModelOptions
  case class JaccardLsh(bands: Int, rows: Int) extends SparseBoolVectorModelOptions

  implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withDiscriminator("type")
  implicit val enc: Encoder[SparseBoolVectorModelOptions] = deriveConfiguredEncoder
  implicit val dec: Decoder[SparseBoolVectorModelOptions] = deriveConfiguredDecoder
}

sealed trait DenseFloatVectorModelOptions
object DenseFloatVectorModelOptions {
  object Exact extends DenseFloatVectorModelOptions

  implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withDiscriminator("type")
  implicit val enc: Encoder[DenseFloatVectorModelOptions] = deriveConfiguredEncoder
  implicit val dec: Decoder[DenseFloatVectorModelOptions] = deriveConfiguredDecoder
}

sealed trait Mapping

object Mapping {
  case class ElastiknnSparseBoolVector(dims: Int, modelOptions: SparseBoolVectorModelOptions) extends Mapping
  case class ElastiknnDenseFloatVector(dims: Int, modelOptions: DenseFloatVectorModelOptions) extends Mapping

  implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withDiscriminator("type")
  implicit val enc: Encoder[Mapping] = deriveConfiguredEncoder
  implicit val dec: Decoder[Mapping] = deriveConfiguredDecoder
}
