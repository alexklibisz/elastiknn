package com.klibisz.elastiknn

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

package object api {

  sealed trait SparseBoolVectorModelOptions
  object SparseBoolVectorModelOptions {
    object Exact extends SparseBoolVectorModelOptions
    object JaccardIndexed extends SparseBoolVectorModelOptions
    case class JaccardLsh(bands: Int, rows: Int) extends SparseBoolVectorModelOptions

    implicit val cfg: Configuration = Mapping.cfg
    implicit val enc: Encoder[SparseBoolVectorModelOptions] = deriveConfiguredEncoder
    implicit val dec: Decoder[SparseBoolVectorModelOptions] = deriveConfiguredDecoder
  }

  sealed trait DenseFloatVectorModelOptions
  object DenseFloatVectorModelOptions {
    object Exact extends DenseFloatVectorModelOptions

    implicit val cfg: Configuration = Mapping.cfg
    implicit val enc: Encoder[DenseFloatVectorModelOptions] = deriveConfiguredEncoder
    implicit val dec: Decoder[DenseFloatVectorModelOptions] = deriveConfiguredDecoder
  }

  sealed trait Mapping

  object Mapping {
    case class SparseBoolVector(dims: Int, modelOptions: SparseBoolVectorModelOptions) extends Mapping
    case class DenseFloatVector(dims: Int, modelOptions: DenseFloatVectorModelOptions) extends Mapping

    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withStrictDecoding
      .withDiscriminator("type")
    implicit val enc: Encoder[Mapping] = deriveConfiguredEncoder
    implicit val dec: Decoder[Mapping] = deriveConfiguredDecoder
  }

  sealed trait Vector

  object Vector {

    implicit val config: Configuration =
      Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withStrictDecoding

    case class SparseBoolVector(trueIndices: Array[Int], totalIndices: Int) extends Vector
    object SparseBoolVector {
      implicit val enc: Encoder[SparseBoolVector] = deriveConfiguredEncoder
      implicit val dec: Decoder[SparseBoolVector] = deriveConfiguredDecoder
    }

    case class DenseFloatVector(values: Array[Float]) extends Vector
    object DenseFloatVector {
      implicit val enc: Encoder[DenseFloatVector] = deriveConfiguredEncoder
      implicit val dec: Decoder[DenseFloatVector] = deriveConfiguredDecoder
    }

    case class IndexedVector(index: String, id: String) extends Vector
    object IndexedVector {
      implicit val enc: Encoder[IndexedVector] = deriveConfiguredEncoder
      implicit val dec: Decoder[IndexedVector] = deriveConfiguredDecoder
    }

    implicit val enc: Encoder[Vector] = deriveConfiguredEncoder
    implicit val dec: Decoder[Vector] = deriveConfiguredDecoder

  }

  object Query {
    case class NearestNeighborsQuery(field: String, vector: Vector)

    object NearestNeighborsQuery {
      implicit val cfg: Configuration =
        Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withStrictDecoding
      implicit val enc: Encoder[NearestNeighborsQuery] = deriveConfiguredEncoder
      implicit val dec: Decoder[NearestNeighborsQuery] = deriveConfiguredDecoder
    }

  }

}
