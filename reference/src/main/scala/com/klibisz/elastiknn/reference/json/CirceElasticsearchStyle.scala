package com.klibisz.elastiknn.reference.json

import io.circe.generic.extras.Configuration
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._
import io.circe.syntax._

object CirceElasticsearchStyle {

  sealed trait SparseBoolVectorModelOptions
  object SparseBoolVectorModelOptions {
    object Exact extends SparseBoolVectorModelOptions
    object JaccardIndexed extends SparseBoolVectorModelOptions
    case class JaccardLsh(numBands: Int, numRows: Int) extends SparseBoolVectorModelOptions
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames
      .withDiscriminator("type")
    implicit val enc: Encoder[SparseBoolVectorModelOptions] = deriveConfiguredEncoder
    implicit val dec: Decoder[SparseBoolVectorModelOptions] = deriveConfiguredDecoder
  }

  case class Mapping(`type`: String, dims: Int, modelOptions: SparseBoolVectorModelOptions)
  object Mapping {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames
    implicit val enc: Encoder[Mapping] = deriveConfiguredEncoder
    implicit val dec: Decoder[Mapping] = deriveConfiguredDecoder
  }

  def main(args: Array[String]): Unit = {
    Seq(
      Mapping("elastiknn_sparse_bool_vector", 33, SparseBoolVectorModelOptions.Exact),
      Mapping("elastiknn_sparse_bool_vector", 33, SparseBoolVectorModelOptions.JaccardIndexed),
      Mapping("elastiknn_sparse_bool_vector", 33, SparseBoolVectorModelOptions.JaccardLsh(100, 1)),
    ).map(_.asJson.spaces2).foreach(println)
  }

}
