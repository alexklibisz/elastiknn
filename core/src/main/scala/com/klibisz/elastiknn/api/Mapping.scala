package com.klibisz.elastiknn.api

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.syntax._

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
  case object Missing extends Mapping
  case class ElastiknnSparseBoolVector(dims: Int) extends Mapping
//  case class ElastiknnSparseBoolVector(dims: Int, modelOptions: SparseBoolVectorModelOptions) extends Mapping
//  case class ElastiknnDenseFloatVector(dims: Int, modelOptions: DenseFloatVectorModelOptions) extends Mapping

  implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withDiscriminator("type")
  implicit val enc: Encoder[Mapping] = deriveConfiguredEncoder
  implicit val dec: Decoder[Mapping] = deriveConfiguredDecoder
}

object Dummy {
  def main(args: Array[String]): Unit = {

//    val mappings: Seq[Mapping] = Seq(
//      Mapping.ElastiknnSparseBoolVector(100, SparseBoolVectorModelOptions.Exact),
//      Mapping.ElastiknnSparseBoolVector(100, SparseBoolVectorModelOptions.JaccardIndexed),
//      Mapping.ElastiknnSparseBoolVector(100, SparseBoolVectorModelOptions.JaccardLsh(100, 3))
//    )
//
//    mappings.foreach { m =>
//      println(m.asJson.spaces2)
//    }

  }
}
