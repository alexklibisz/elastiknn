package com.klibisz.elastiknn.api

import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import com.klibisz.elastiknn.api.Vector.{DenseFloatVector, IndexedVector, SparseBoolVector}
import io.circe
import io.circe.Decoder.Result
import io.circe.generic.extras.Configuration
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject, ParsingFailure}
import io.circe.generic.extras.semiauto._

import scala.language.implicitConversions

trait ElasticsearchEncoder[A] extends Encoder[A] {
  def apply(a: A): Json
}

trait ElasticsearchDecoder[A] extends Decoder[A] {
  def apply(c: HCursor): Decoder.Result[A]
}

trait ElasticsearchCodec[A] extends Codec[A]

object ElasticsearchCodec {

  private val TYPE = "type"
  private val DIMS = "dims"
  private val MOPTS = "model_options"
  private val EXACT = "exact"
  private val FIELD = "field"
  private val VECTOR = "vector"
  private val INDEX = "index"

  private type ESC[T] = ElasticsearchCodec[T]
  private val esc = this

  private def fail[T](msg: String): Either[DecodingFailure, T] = Left(DecodingFailure(msg, List.empty))

  private implicit def jsonObjToJson(jo: JsonObject): Json = Json.fromJsonObject(jo)
  private implicit def intToJson(i: Int): Json = Json.fromInt(i)
  private implicit def strToJson(s: String): Json = Json.fromString(s)
  private implicit class EitherWithOrElse[+L1, +R1](either: Either[L1, R1]) {
    def orElse[L2 >: L1, R2 >: R1](other: Either[L2, R2]): Either[L2, R2] = if (either.isRight) either else other
  }

  private val b64 = BaseEncoding.base64()

  def encode[T: ElasticsearchCodec](t: T): Json = implicitly[ElasticsearchCodec[T]].apply(t)
  def encodeB64[T: ElasticsearchCodec](t: T): String = b64.encode(encode(t).noSpaces.getBytes)
  def decode[T: ElasticsearchCodec](c: HCursor): Either[DecodingFailure, T] = implicitly[ElasticsearchCodec[T]].apply(c)
  def decodeJson[T: ElasticsearchCodec](j: Json): Either[DecodingFailure, T] = implicitly[ElasticsearchCodec[T]].decodeJson(j)
  def decodeB64[T: ElasticsearchCodec](s: String): Either[circe.Error, T] = parseB64(s).flatMap(decodeJson[T])
  def parse(s: String): Either[circe.Error, Json] = io.circe.parser.parse(s)
  def parseB64(s: String): Either[circe.Error, Json] = parse(new String(b64.decode(s)))

  // Danger zone.
  def decodeGet[T: ElasticsearchCodec](c: HCursor): T = decode[T](c).toTry.get
  def decodeJsonGet[T: ElasticsearchCodec](j: Json): T = decodeJson[T](j).toTry.get
  def decodeB64Get[T: ElasticsearchCodec](s: String): T = decodeB64[T](s).toTry.get
  def parseGet[T: ElasticsearchCodec](s: String): Json = parse(s).toTry.get
  def parseB64Get[T: ElasticsearchCodec](s: String): Json = parseB64(s).toTry.get

  implicit val sparseBoolVector: ESC[SparseBoolVector] = new ESC[SparseBoolVector] {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withStrictDecoding
    private val enc: Encoder[SparseBoolVector] = deriveConfiguredEncoder[SparseBoolVector]
    private val dec: Decoder[SparseBoolVector] = deriveConfiguredDecoder[SparseBoolVector]
    override def apply(t: SparseBoolVector): Json = enc(t)
    override def apply(c: HCursor): Either[DecodingFailure, SparseBoolVector] = dec.apply(c)
  }

  implicit val denseFloatVector: ESC[DenseFloatVector] = new ESC[DenseFloatVector] {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withStrictDecoding
    private val enc: Encoder[DenseFloatVector] = deriveConfiguredEncoder[DenseFloatVector]
    private val dec: Decoder[DenseFloatVector] = deriveConfiguredDecoder[DenseFloatVector]
    override def apply(t: DenseFloatVector): Json = enc(t)
    override def apply(c: HCursor): Either[DecodingFailure, DenseFloatVector] = dec.apply(c)
  }

  implicit val indexedVector: ESC[IndexedVector] = new ESC[IndexedVector] {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames.withStrictDecoding
    private val enc: Encoder[IndexedVector] = deriveConfiguredEncoder[IndexedVector]
    private val dec: Decoder[IndexedVector] = deriveConfiguredDecoder[IndexedVector]
    override def apply(t: IndexedVector): Json = enc(t)
    override def apply(c: HCursor): Either[DecodingFailure, IndexedVector] = dec.apply(c)
  }

  implicit val vector: ESC[api.Vector] = new ESC[api.Vector] {
    override def apply(t: Vector): Json = t match {
      case ixv: IndexedVector    => esc.encode(ixv)
      case sbv: SparseBoolVector => esc.encode(sbv)
      case dfv: DenseFloatVector => esc.encode(dfv)
    }

    override def apply(c: HCursor): Either[DecodingFailure, Vector] =
      denseFloatVector(c).orElse(sparseBoolVector(c)).orElse(indexedVector(c))
  }

  implicit val jaccardLshModelOptions: ESC[SparseBoolVectorModelOptions.JaccardLsh] = new ESC[SparseBoolVectorModelOptions.JaccardLsh] {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames
    private val enc: Encoder[SparseBoolVectorModelOptions.JaccardLsh] = deriveConfiguredEncoder
    private val dec: Decoder[SparseBoolVectorModelOptions.JaccardLsh] = deriveConfiguredDecoder
    override def apply(t: SparseBoolVectorModelOptions.JaccardLsh): Json = enc(t)
    override def apply(c: HCursor): Either[DecodingFailure, SparseBoolVectorModelOptions.JaccardLsh] = dec.apply(c)
  }

  implicit val sparseBoolVectorModelOptions: ESC[SparseBoolVectorModelOptions] = new ESC[SparseBoolVectorModelOptions] {

    private val JACCIX = "jaccard_indexed"
    private val JACCLSH = "jaccard_lsh"

    override def apply(t: SparseBoolVectorModelOptions): Json = t match {
      case SparseBoolVectorModelOptions.JaccardIndexed   => JsonObject(TYPE -> JACCIX)
      case jlsh: SparseBoolVectorModelOptions.JaccardLsh => JsonObject(TYPE -> JACCLSH).deepMerge(esc.encode(jlsh))
    }

    override def apply(c: HCursor): Either[DecodingFailure, SparseBoolVectorModelOptions] = {
      for {
        typ <- c.downField(TYPE).as[String]
        mopts <- typ match {
          case JACCIX  => Right(SparseBoolVectorModelOptions.JaccardIndexed)
          case JACCLSH => esc.decode[SparseBoolVectorModelOptions.JaccardLsh](c)
          case other   => fail(s"Expected $TYPE to be one of ($EXACT, $JACCIX, $JACCLSH) but got $other")
        }
      } yield mopts
    }
  }

  implicit val denseFloatVectorModelOptions: ESC[DenseFloatVectorModelOptions] = new ESC[DenseFloatVectorModelOptions] {
    private val ANGLSH = "angular_lsh"
    override def apply(t: DenseFloatVectorModelOptions): Json = t match {
      case _: DenseFloatVectorModelOptions.AngularLsh => JsonObject(TYPE -> ANGLSH)
    }
    override def apply(c: HCursor): Either[DecodingFailure, DenseFloatVectorModelOptions] =
      for {
        typ <- c.downField(TYPE).as[String]
        mopts <- typ match {
          case other => fail(s"Expected $TYPE to be one of ($EXACT) but got $other")
        }
      } yield mopts
  }

  implicit val mappingDenseFloatVector: ESC[Mapping.DenseFloatVector] = new ESC[Mapping.DenseFloatVector] {
    override def apply(t: Mapping.DenseFloatVector): Json = t.modelOptions match {
      case Some(mopts) => JsonObject(DIMS -> t.dims, MOPTS -> esc.encode(mopts))
      case None        => JsonObject(DIMS -> t.dims)
    }
    override def apply(c: HCursor): Either[DecodingFailure, Mapping.DenseFloatVector] =
      for {
        dims <- c.downField(DIMS).as[Int]
        mopts <- c.value.findAllByKey(MOPTS).headOption match {
          case Some(moptsJson) => esc.decodeJson[DenseFloatVectorModelOptions](moptsJson).map(Some(_))
          case None            => Right(None)
        }
      } yield Mapping.DenseFloatVector(dims, mopts)
  }

  implicit val mappingSparseBoolVector: ESC[Mapping.SparseBoolVector] = new ESC[Mapping.SparseBoolVector] {
    override def apply(t: Mapping.SparseBoolVector): Json = t.modelOptions match {
      case Some(mopts) => JsonObject(DIMS -> t.dims, MOPTS -> esc.encode(mopts))
      case None        => JsonObject(DIMS -> t.dims)
    }

    override def apply(c: HCursor): Either[DecodingFailure, Mapping.SparseBoolVector] =
      for {
        dims <- c.downField(DIMS).as[Int]
        mopts <- c.value.findAllByKey(MOPTS).headOption match {
          case Some(moptsJson) => esc.decodeJson[SparseBoolVectorModelOptions](moptsJson).map(Some(_))
          case None            => Right(None)
        }
      } yield Mapping.SparseBoolVector(dims, mopts)
  }

  implicit val mapping: ESC[Mapping] = new ESC[Mapping] {

    val EKNNSBV = s"${ELASTIKNN_NAME}_sparse_bool_vector"
    val EKNNDFV = s"${ELASTIKNN_NAME}_dense_float_vector"

    override def apply(t: Mapping): Json = t match {
      case sbv: Mapping.SparseBoolVector => JsonObject(TYPE -> EKNNSBV).deepMerge(esc.encode(sbv))
      case dfv: Mapping.DenseFloatVector => JsonObject(TYPE -> EKNNDFV).deepMerge(esc.encode(dfv))
    }

    override def apply(c: HCursor): Either[DecodingFailure, Mapping] =
      for {
        typ <- c.downField(TYPE).as[String]
        mapping <- typ match {
          case EKNNSBV => ElasticsearchCodec.decode[Mapping.SparseBoolVector](c)
          case EKNNDFV => ElasticsearchCodec.decode[Mapping.DenseFloatVector](c)
          case other   => fail(s"Expected $TYPE to be one of ($EKNNDFV, $EKNNSBV) but got $other")
        }
      } yield mapping

  }

  implicit val nearestNeighborsQuery: ESC[Query.NearestNeighborsQuery] = new ESC[Query.NearestNeighborsQuery] {
    val QUERY_OPTIONS = "query_options"
    override def apply(t: Query.NearestNeighborsQuery): Json =
      JsonObject(FIELD -> t.field, INDEX -> t.index, VECTOR -> esc.encode(t.vector))
    override def apply(c: HCursor): Either[DecodingFailure, Query.NearestNeighborsQuery] = {
      for {
        index <- c.downField(INDEX).as[String]
        field <- c.downField(FIELD).as[String]
        vecJson <- c.downField(VECTOR).as[Json]
        vec <- esc.decodeJson[api.Vector](vecJson)
//        qoptsJson <- c.downField(QUERY_OPTIONS).as[Json]
//        qopts <- esc.decode[api.QueryOptions](qoptsJson)
      } yield Query.NearestNeighborsQuery(index, field, vec, QueryOptions.Exact(Similarity.Jaccard))
    }
  }

}
