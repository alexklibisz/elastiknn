package com.klibisz.elastiknn.api

import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.api.Vec.{DenseFloat, Indexed, SparseBool}
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import io.circe
import io.circe.Decoder.Result
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.generic.semiauto.deriveCodec
import io.circe._

import scala.language.implicitConversions

trait ElasticsearchEncoder[A] extends Encoder[A] {
  def apply(a: A): Json
}

trait ElasticsearchDecoder[A] extends Decoder[A] {
  def apply(c: HCursor): Decoder.Result[A]
}

trait ElasticsearchCodec[A] extends Codec[A]

private object Keys {
  val ANGULAR = "angular"
  val ANGULAR_LSH = "angular_lsh"
  val DIMS = "dims"
  val EKNN_DENSE_FLOAT_VECTOR = s"${ELASTIKNN_NAME}_dense_float_vector"
  val EKNN_SPARSE_BOOL_VECTOR = s"${ELASTIKNN_NAME}_sparse_bool_vector"
  val MODEL_OPTIONS = "model_options"
  val EXACT = "exact"
  val FIELD = "field"
  val HAMMING = "hamming"
  val INDEX = "index"
  val JACCARD = "jaccard"
  val JACCARD_INDEXED = "jaccard_indexed"
  val JACCARD_LSH = "jaccard_lsh"
  val L1 = "l1"
  val L2 = "l2"
  val QUERY_OPTIONS = "query_options"
  val SIMILARITY = "similarity"
  val TYPE = "type"
  val VECTOR = "vector"
}

object ElasticsearchCodec {

  import Keys._

  private def apply[A](codec: Codec[A]): ElasticsearchCodec[A] = new ESC[A] {
    override def apply(c: HCursor): Result[A] = codec(c)
    override def apply(a: A): Json = codec(a)
  }

  private def apply[A](encoder: Encoder[A], decoder: Decoder[A]): ESC[A] = apply(Codec.from(decoder, encoder))

  private type ESC[T] = ElasticsearchCodec[T]
  private val esc = this

  private def fail[T](msg: String): Either[DecodingFailure, T] = Left(DecodingFailure(msg, List.empty))
  private def failTypes[T](good: Seq[String], bad: String): Either[DecodingFailure, T] =
    Left(DecodingFailure(s"Expected $TYPE to be one of (${good.mkString(", ")}) but got $bad", List.empty))

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

  implicit val similarity: ESC[Similarity] = new ESC[Similarity] {
    // The apply methods account for lowercasing. Otherwise we could just use the derived codec.
    private val codec = deriveEnumerationCodec[Similarity]
    override def apply(c: HCursor): Result[Similarity] =
      for {
        str <- c.as[String]
        cur = Json.fromString(str.head.toUpper + str.tail.toLowerCase).hcursor
        sim <- codec(cur)
      } yield sim
    override def apply(a: Similarity): Json =
      codec(a).asString.map(_.toLowerCase).map(Json.fromString).getOrElse(codec(a))
  }

  implicit val denseFloatVector: ESC[DenseFloat] = ElasticsearchCodec(deriveCodec)
  implicit val indexedVector: ESC[Indexed] = ElasticsearchCodec(deriveCodec)
  implicit val sparseBoolVector: ESC[SparseBool] = {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames
    ElasticsearchCodec(deriveConfiguredCodec)
  }

  implicit val vector: ESC[api.Vec] = new ESC[api.Vec] {
    override def apply(t: Vec): Json = t match {
      case ixv: Indexed    => encode(ixv)
      case sbv: SparseBool => encode(sbv)
      case dfv: DenseFloat => encode(dfv)
    }
    override def apply(c: HCursor): Either[DecodingFailure, Vec] =
      denseFloatVector(c).orElse(sparseBoolVector(c)).orElse(indexedVector(c))
  }

  implicit val jaccardLshModelOptions: ESC[SparseBoolVectorModelOptions.JaccardLsh] = ElasticsearchCodec(deriveCodec)

  implicit val sparseBoolVectorModelOptions: ESC[SparseBoolVectorModelOptions] = new ESC[SparseBoolVectorModelOptions] {

    override def apply(t: SparseBoolVectorModelOptions): Json = t match {
      case SparseBoolVectorModelOptions.JaccardIndexed   => JsonObject(TYPE -> JACCARD_INDEXED)
      case jlsh: SparseBoolVectorModelOptions.JaccardLsh => JsonObject(TYPE -> JACCARD_LSH).deepMerge(encode(jlsh))
    }

    override def apply(c: HCursor): Either[DecodingFailure, SparseBoolVectorModelOptions] = {
      for {
        typ <- c.downField(TYPE).as[String]
        mopts <- typ match {
          case JACCARD_INDEXED => Right(SparseBoolVectorModelOptions.JaccardIndexed)
          case JACCARD_LSH     => decode[SparseBoolVectorModelOptions.JaccardLsh](c)
          case other           => failTypes(Seq(JACCARD_INDEXED, JACCARD_LSH), other)
        }
      } yield mopts
    }
  }

  implicit val denseFloatVectorModelOptions: ESC[DenseFloatVectorModelOptions] = new ESC[DenseFloatVectorModelOptions] {
    override def apply(t: DenseFloatVectorModelOptions): Json = t match {
      case _: DenseFloatVectorModelOptions.AngularLsh => JsonObject(TYPE -> ANGULAR_LSH)
    }
    override def apply(c: HCursor): Either[DecodingFailure, DenseFloatVectorModelOptions] =
      for {
        typ <- c.downField(TYPE).as[String]
        mopts <- typ match {
          case ANGULAR_LSH => Right(DenseFloatVectorModelOptions.AngularLsh())
          case other       => failTypes(Seq(ANGULAR_LSH), other)
        }
      } yield mopts
  }

  implicit val mappingDenseFloatVector: ESC[Mapping.DenseFloat] = new ESC[Mapping.DenseFloat] {
    override def apply(t: Mapping.DenseFloat): Json = t.modelOptions match {
      case Some(mopts) => JsonObject(DIMS -> t.dims, MODEL_OPTIONS -> encode(mopts))
      case None        => JsonObject(DIMS -> t.dims)
    }
    override def apply(c: HCursor): Either[DecodingFailure, Mapping.DenseFloat] =
      for {
        dims <- c.downField(DIMS).as[Int]
        mopts <- c.value.findAllByKey(MODEL_OPTIONS).headOption match {
          case Some(moptsJson) => esc.decodeJson[DenseFloatVectorModelOptions](moptsJson).map(Some(_))
          case None            => Right(None)
        }
      } yield Mapping.DenseFloat(dims, mopts)
  }

  implicit val mappingSparseBoolVector: ESC[Mapping.SparseBool] = new ESC[Mapping.SparseBool] {
    override def apply(t: Mapping.SparseBool): Json = t.modelOptions match {
      case Some(mopts) => JsonObject(DIMS -> t.dims, MODEL_OPTIONS -> encode(mopts))
      case None        => JsonObject(DIMS -> t.dims)
    }

    override def apply(c: HCursor): Either[DecodingFailure, Mapping.SparseBool] =
      for {
        dims <- c.downField(DIMS).as[Int]
        mopts <- c.value.findAllByKey(MODEL_OPTIONS).headOption match {
          case Some(moptsJson) => esc.decodeJson[SparseBoolVectorModelOptions](moptsJson).map(Some(_))
          case None            => Right(None)
        }
      } yield Mapping.SparseBool(dims, mopts)
  }

  implicit val mapping: ESC[Mapping] = new ESC[Mapping] {
    override def apply(t: Mapping): Json = t match {
      case sbv: Mapping.SparseBool => JsonObject(TYPE -> EKNN_SPARSE_BOOL_VECTOR).deepMerge(encode(sbv))
      case dfv: Mapping.DenseFloat => JsonObject(TYPE -> EKNN_DENSE_FLOAT_VECTOR).deepMerge(encode(dfv))
    }

    override def apply(c: HCursor): Either[DecodingFailure, Mapping] =
      for {
        typ <- c.downField(TYPE).as[String]
        mapping <- typ match {
          case EKNN_SPARSE_BOOL_VECTOR => ElasticsearchCodec.decode[Mapping.SparseBool](c)
          case EKNN_DENSE_FLOAT_VECTOR => ElasticsearchCodec.decode[Mapping.DenseFloat](c)
          case other                   => failTypes(Seq(EKNN_DENSE_FLOAT_VECTOR, EKNN_SPARSE_BOOL_VECTOR), other)
        }
      } yield mapping

  }

  implicit val exactQueryOptions: ESC[QueryOptions.Exact] = ElasticsearchCodec(deriveCodec)
  implicit val jaccardLshQueryOptions: ESC[QueryOptions.JaccardLsh] = ElasticsearchCodec(deriveCodec)

  implicit val queryOptions: ESC[QueryOptions] = new ESC[QueryOptions] {
    override def apply(c: HCursor): Result[QueryOptions] =
      for {
        typ <- c.downField(TYPE).as[String]
        opts <- typ match {
          case EXACT           => decode[QueryOptions.Exact](c)
          case JACCARD_LSH     => decode[QueryOptions.JaccardLsh](c)
          case JACCARD_INDEXED => Right(QueryOptions.JaccardIndexed)
        }
      } yield opts
    override def apply(a: QueryOptions): Json = a match {
      case QueryOptions.Exact(sim)       => JsonObject(TYPE -> EXACT, SIMILARITY -> encode[Similarity](sim))
      case QueryOptions.JaccardIndexed   => JsonObject(TYPE -> JACCARD_INDEXED)
      case jlsh: QueryOptions.JaccardLsh => JsonObject(TYPE -> JACCARD_LSH).deepMerge(encode(jlsh))
    }
  }

  implicit val nearestNeighborsQuery: ESC[Query.NearestNeighborsQuery] = new ESC[Query.NearestNeighborsQuery] {
    override def apply(t: Query.NearestNeighborsQuery): Json =
      JsonObject(FIELD -> t.field, INDEX -> t.index, VECTOR -> encode(t.vector))
    override def apply(c: HCursor): Either[DecodingFailure, Query.NearestNeighborsQuery] = {
      for {
        index <- c.downField(INDEX).as[String]
        field <- c.downField(FIELD).as[String]
        vecJson <- c.downField(VECTOR).as[Json]
        vec <- esc.decodeJson[api.Vec](vecJson)
        qoptsJson <- c.downField(QUERY_OPTIONS).as[Json]
        qopts <- esc.decodeJson[QueryOptions](qoptsJson)
      } yield Query.NearestNeighborsQuery(index, field, vec, qopts)
    }
  }

}
