package com.klibisz.elastiknn.api

import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import com.klibisz.elastiknn.api.Vector.{DenseFloatVector, IndexedVector, SparseBoolVector}
import io.circe
import io.circe.generic.extras.Configuration
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.extras.semiauto._

import scala.language.implicitConversions

trait ElasticsearchCodec[T] {
  def encode(t: T): Json
  def decode(j: Json): Either[DecodingFailure, T]
  final def parse(s: String): Either[circe.Error, T] = io.circe.parser.parse(s).flatMap(decode)
}

object ElasticsearchCodec {

  private val TYPE = "type"
  private val DIMS = "dims"
  private val MOPTS = "model_options"
  private val EXACT = "exact"
  private val FIELD = "field"
  private val VECTOR = "vector"
  private val INDEX = "index"

  private type ESC[T] = ElasticsearchCodec[T]

  private def fail[T](msg: String): Either[DecodingFailure, T] = Left(DecodingFailure(msg, List.empty))

  private implicit def jsonObjToJson(jo: JsonObject): Json = Json.fromJsonObject(jo)
  private implicit def intToJson(i: Int): Json = Json.fromInt(i)
  private implicit def strToJson(s: String): Json = Json.fromString(s)
  private implicit class EitherWithOrElse[+L1, +R1](either: Either[L1, R1]) {
    def orElse[L2 >: L1, R2 >: R1](other: Either[L2, R2]): Either[L2, R2] = if (either.isRight) either else other
  }

  private val b64 = BaseEncoding.base64()

  def encode[T: ElasticsearchCodec](t: T): Json = implicitly[ElasticsearchCodec[T]].encode(t)
  def encodeB64[T: ElasticsearchCodec](t: T): String = b64.encode(encode(t).noSpaces.getBytes)
  def decode[T: ElasticsearchCodec](j: Json): Either[DecodingFailure, T] = implicitly[ElasticsearchCodec[T]].decode(j)
  def decodeGet[T: ElasticsearchCodec](j: Json): T = decode[T](j).toTry.get
  def parse[T: ElasticsearchCodec](s: String): Either[circe.Error, T] = implicitly[ElasticsearchCodec[T]].parse(s)
  def parseB64[T: ElasticsearchCodec](s: String): Either[circe.Error, T] = parse[T](new String(b64.decode(s)))
  def parseGet[T: ElasticsearchCodec](s: String): T = parse[T](s).toTry.get
  def parseGetB64[T: ElasticsearchCodec](s: String): T = parseB64[T](s).toTry.get

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
      case ixv: IndexedVector    => ElasticsearchCodec.encode(ixv)
      case sbv: SparseBoolVector => ElasticsearchCodec.encode(sbv)
      case dfv: DenseFloatVector => ElasticsearchCodec.encode(dfv)
    }

    override def decode(j: Json): Either[DecodingFailure, Vector] =
      denseFloatVector
        .decode(j)
        .orElse(sparseBoolVector.decode(j))
        .orElse(indexedVector.decode(j))
  }

  implicit val jaccardLshModelOptions: ESC[SparseBoolVectorModelOptions.JaccardLsh] = new ESC[SparseBoolVectorModelOptions.JaccardLsh] {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames
    private val enc: Encoder[SparseBoolVectorModelOptions.JaccardLsh] = deriveConfiguredEncoder
    private val dec: Decoder[SparseBoolVectorModelOptions.JaccardLsh] = deriveConfiguredDecoder
    override def encode(t: SparseBoolVectorModelOptions.JaccardLsh): Json = enc(t)
    override def decode(j: Json): Either[DecodingFailure, SparseBoolVectorModelOptions.JaccardLsh] = dec.decodeJson(j)
  }

  implicit val sparseBoolVectorModelOptions: ESC[SparseBoolVectorModelOptions] = new ESC[SparseBoolVectorModelOptions] {

    private val JACCIX = "jaccard_indexed"
    private val JACCLSH = "jaccard_lsh"

    override def encode(t: SparseBoolVectorModelOptions): Json = t match {
      case SparseBoolVectorModelOptions.Exact            => JsonObject(TYPE -> EXACT)
      case SparseBoolVectorModelOptions.JaccardIndexed   => JsonObject(TYPE -> JACCIX)
      case jlsh: SparseBoolVectorModelOptions.JaccardLsh => JsonObject(TYPE -> JACCLSH).deepMerge(ElasticsearchCodec.encode(jlsh))
    }

    override def decode(j: Json): Either[DecodingFailure, SparseBoolVectorModelOptions] = {
      val c = j.hcursor
      for {
        typ <- c.downField(TYPE).as[String]
        mopts <- typ match {
          case EXACT   => Right(SparseBoolVectorModelOptions.Exact)
          case JACCIX  => Right(SparseBoolVectorModelOptions.JaccardIndexed)
          case JACCLSH => implicitly[ESC[SparseBoolVectorModelOptions.JaccardLsh]].decode(j)
          case other   => fail(s"Expected $TYPE to be one of ($EXACT, $JACCIX, $JACCLSH) but got $other")
        }
      } yield mopts
    }
  }

  implicit val denseFloatVectorModelOptions: ESC[DenseFloatVectorModelOptions] = new ESC[DenseFloatVectorModelOptions] {
    override def encode(t: DenseFloatVectorModelOptions): Json = t match {
      case DenseFloatVectorModelOptions.Exact => JsonObject(TYPE -> EXACT)
    }
    override def decode(j: Json): Either[DecodingFailure, DenseFloatVectorModelOptions] = {
      val c = j.hcursor
      for {
        typ <- c.downField(TYPE).as[String]
        mopts <- typ match {
          case EXACT => Right(DenseFloatVectorModelOptions.Exact)
          case other => fail(s"Expect $TYPE to be one of ($EXACT) but got $other")
        }
      } yield mopts
    }
  }

  implicit val mappingDenseFloatVector: ESC[Mapping.DenseFloatVector] = new ESC[Mapping.DenseFloatVector] {
    override def encode(t: Mapping.DenseFloatVector): Json = JsonObject(DIMS -> t.dims, MOPTS -> ElasticsearchCodec.encode(t.modelOptions))
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
    override def encode(t: Mapping.SparseBoolVector): Json = JsonObject(DIMS -> t.dims, MOPTS -> ElasticsearchCodec.encode(t.modelOptions))
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
      case sbv: Mapping.SparseBoolVector => JsonObject(TYPE -> EKNNSBV).deepMerge(ElasticsearchCodec.encode(sbv))
      case dfv: Mapping.DenseFloatVector => JsonObject(TYPE -> EKNNDFV).deepMerge(ElasticsearchCodec.encode(dfv))
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

  implicit val nearestNeighborsQuery: ESC[Query.NearestNeighborsQuery] = new ESC[Query.NearestNeighborsQuery] {
    override def encode(t: Query.NearestNeighborsQuery): Json =
      JsonObject(FIELD -> t.field, INDEX -> t.index, VECTOR -> ElasticsearchCodec.encode(t.vector))
    override def decode(j: Json): Either[DecodingFailure, Query.NearestNeighborsQuery] = {
      val c = j.hcursor
      for {
        index <- c.downField(INDEX).as[String]
        field <- c.downField(FIELD).as[String]
        vecJson <- c.downField(VECTOR).as[Json]
        vec <- implicitly[ESC[api.Vector]].decode(vecJson)
      } yield Query.NearestNeighborsQuery(index, field, vec)
    }
  }

}
