package com.klibisz.elastiknn.api

import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import com.klibisz.elastiknn.api.Vector.{DenseFloatVector, IndexedVector, SparseBoolVector}
import io.circe.generic.extras.Configuration
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.extras.semiauto._

import scala.language.implicitConversions

trait ElasticsearchCodec[T] {
  def encode(t: T): Json
  def decode(j: Json): Either[DecodingFailure, T]
}

object ElasticsearchCodec {

  private val TYPE = "type"
  private val DIMS = "dims"
  private val MOPTS = "model_options"

  private type ESC[T] = ElasticsearchCodec[T]

  private def fail[T](msg: String): Either[DecodingFailure, T] = Left(DecodingFailure(msg, List.empty))

  private implicit def jsonObjToJson(jo: JsonObject): Json = Json.fromJsonObject(jo)
  private implicit def intToJson(i: Int): Json = Json.fromInt(i)
  private implicit def strToJson(s: String): Json = Json.fromString(s)
  private implicit class EitherWithOrElse[+L1, +R1](either: Either[L1, R1]) {
    def orElse[L2 >: L1, R2 >: R1](other: Either[L2, R2]): Either[L2, R2] = if (either.isRight) either else other
  }

  implicit val sparseBoolVector: ESC[SparseBoolVector] = new ESC[SparseBoolVector] {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withStrictDecoding
    private val enc: Encoder[SparseBoolVector] = deriveConfiguredEncoder[SparseBoolVector]
    private val dec: Decoder[SparseBoolVector] = deriveConfiguredDecoder[SparseBoolVector]
    override def encode(t: SparseBoolVector): Json = enc(t)
    override def decode(j: Json): Either[DecodingFailure, SparseBoolVector] = dec.decodeJson(j)
  }

  implicit val denseFloatVector: ESC[DenseFloatVector] = new ESC[DenseFloatVector] {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withStrictDecoding
    private val enc: Encoder[DenseFloatVector] = deriveConfiguredEncoder[DenseFloatVector]
    private val dec: Decoder[DenseFloatVector] = deriveConfiguredDecoder[DenseFloatVector]
    override def encode(t: DenseFloatVector): Json = enc(t)
    override def decode(j: Json): Either[DecodingFailure, DenseFloatVector] = dec.decodeJson(j)
  }

  implicit val indexedVector: ESC[IndexedVector] = new ESC[IndexedVector] {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withStrictDecoding
    private val enc: Encoder[IndexedVector] = deriveConfiguredEncoder[IndexedVector]
    private val dec: Decoder[IndexedVector] = deriveConfiguredDecoder[IndexedVector]
    override def encode(t: IndexedVector): Json = enc(t)
    override def decode(j: Json): Either[DecodingFailure, IndexedVector] = dec.decodeJson(j)
  }

  implicit val vector: ESC[api.Vector] = new ESC[api.Vector] {
    override def encode(t: Vector): Json = t match {
      case ixv: IndexedVector    => implicitly[ESC[IndexedVector]].encode(ixv)
      case sbv: SparseBoolVector => implicitly[ESC[SparseBoolVector]].encode(sbv)
      case dfv: DenseFloatVector => implicitly[ESC[DenseFloatVector]].encode(dfv)
    }

    override def decode(j: Json): Either[DecodingFailure, Vector] =
      denseFloatVector
        .decode(j)
        .orElse(sparseBoolVector.decode(j))
        .orElse(indexedVector.decode(j))
  }

  implicit val sparseBoolVectorModelOptions: ESC[SparseBoolVectorModelOptions] = new ESC[SparseBoolVectorModelOptions] {

    private val EXACT = "exact"
    private val JACCIX = "jaccard_indexed"
    private val JACCLSH = "jaccard_lsh"
    private val BANDS = "bands"
    private val ROWS = "rows"

    override def encode(t: SparseBoolVectorModelOptions): Json = t match {
      case SparseBoolVectorModelOptions.Exact          => JsonObject(TYPE -> EXACT)
      case SparseBoolVectorModelOptions.JaccardIndexed => JsonObject(TYPE -> JACCIX)
      case SparseBoolVectorModelOptions.JaccardLsh(bands: Int, rows: Int) =>
        JsonObject(TYPE -> JACCLSH, BANDS -> bands, ROWS -> rows)
    }

    override def decode(j: Json): Either[DecodingFailure, SparseBoolVectorModelOptions] = {
      val c = j.hcursor
      for {
        typ <- c.downField(TYPE).as[String]
        mopts <- typ match {
          case EXACT  => Right(SparseBoolVectorModelOptions.Exact)
          case JACCIX => Right(SparseBoolVectorModelOptions.JaccardIndexed)
          case JACCLSH =>
            for {
              bands <- c.downField(BANDS).as[Int]
              rows <- c.downField(ROWS).as[Int]
            } yield SparseBoolVectorModelOptions.JaccardLsh(bands, rows)
          case other => fail(s"Expected type $TYPE $EXACT, $JACCIX, or $JACCLSH, but got $other")
        }
      } yield mopts
    }
  }

  implicit val denseFloatVectorModelOptions: ESC[DenseFloatVectorModelOptions] = new ESC[DenseFloatVectorModelOptions] {
    override def encode(t: DenseFloatVectorModelOptions): Json = ???
    override def decode(j: Json): Either[DecodingFailure, DenseFloatVectorModelOptions] = ???
  }

  implicit val mappingDenseFloatVector: ESC[Mapping.DenseFloatVector] = new ESC[Mapping.DenseFloatVector] {
    override def encode(t: Mapping.DenseFloatVector): Json = JsonObject(
      DIMS -> t.dims,
      MOPTS -> implicitly[ESC[DenseFloatVectorModelOptions]].encode(t.modelOptions)
    )
    override def decode(j: Json): Either[DecodingFailure, Mapping.DenseFloatVector] = {
      val c = j.hcursor
      for {
        dims <- c.downField(DIMS).as[Int]
        moptsJson <- c.downField(MOPTS).as[Json]
        mopts <- implicitly[ESC[DenseFloatVectorModelOptions]].decode(moptsJson)
      } yield Mapping.DenseFloatVector(dims, mopts)
    }
  }

  implicit val mappingSparseBoolVector: ESC[Mapping.SparseBoolVector] = new ESC[Mapping.SparseBoolVector] {
    override def encode(t: Mapping.SparseBoolVector): Json = JsonObject(
      DIMS -> t.dims,
      MOPTS -> implicitly[ESC[SparseBoolVectorModelOptions]].encode(t.modelOptions)
    )
    override def decode(j: Json): Either[DecodingFailure, Mapping.SparseBoolVector] = {
      val c = j.hcursor
      for {
        dims <- c.downField(DIMS).as[Int]
        moptsJson <- c.downField(MOPTS).as[Json]
        mopts <- implicitly[ESC[SparseBoolVectorModelOptions]].decode(moptsJson)
      } yield Mapping.SparseBoolVector(dims, mopts)
    }
  }

  implicit val mapping: ESC[Mapping] = new ESC[Mapping] {

    val EKNNSBV = s"${ELASTIKNN_NAME}_sparse_bool_vector"
    val EKNNDFV = s"${ELASTIKNN_NAME}_dense_float_vector"

    override def encode(t: Mapping): Json = t match {
      case sbv: Mapping.SparseBoolVector =>
        JsonObject(TYPE -> EKNNSBV).deepMerge(implicitly[ESC[Mapping.SparseBoolVector]].encode(sbv))
      case dfv: Mapping.DenseFloatVector =>
        JsonObject(TYPE -> EKNNDFV).deepMerge(implicitly[ESC[Mapping.DenseFloatVector]].encode(dfv))
    }

    override def decode(j: Json): Either[DecodingFailure, Mapping] = {
      val c = j.hcursor
      for {
        typ <- c.downField(TYPE).as[String]
        mapping <- typ match {
          case EKNNSBV => implicitly[ESC[Mapping.SparseBoolVector]].decode(j)
          case EKNNDFV => implicitly[ESC[Mapping.DenseFloatVector]].decode(j)
          case other   => fail(s"Expected $TYPE to be one of ($EKNNDFV, $EKNNSBV) but got $other")
        }
      } yield mapping
    }

  }

}
