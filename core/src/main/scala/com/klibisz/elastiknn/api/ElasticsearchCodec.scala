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

/**
  * Typeclass for handling idiomatic elasticsearch JSON using Circe.
  * @tparam A
  */
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
  val LSH = "lsh"
  val MODEL = "model"
  val QUERY_OPTIONS = "query_options"
  val SIMILARITY = "similarity"
  val SPARSE_INDEXED = "sparse_indexed"
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
  private def failTypes[T](field: String, good: Seq[String], bad: String): Either[DecodingFailure, T] =
    fail(s"Expected field $field to be one of (${good.mkString(", ")}) but got $bad")
  private def failTypes[T](good: Seq[String], bad: String): Either[DecodingFailure, T] = failTypes(TYPE, good, bad)

  private implicit def jsonObjToJson(jo: JsonObject): Json = Json.fromJsonObject(jo)
  private implicit def intToJson(i: Int): Json = Json.fromInt(i)
  private implicit def strToJson(s: String): Json = Json.fromString(s)
  private implicit class EitherSyntax[+L1, +R1](either: Either[L1, R1]) {
    def orElse[L2 >: L1, R2 >: R1](other: Either[L2, R2]): Either[L2, R2] = if (either.isRight) either else other
  }
  private implicit class JsonObjectSyntax(j: JsonObject) {
    def ++(other: Json): Json = j.deepMerge(other)
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
    // Circe's default enumeration codec is case-sensitive and gives useless errors.
    override def apply(c: HCursor): Result[Similarity] =
      for {
        str <- c.as[String]
        sim <- str.toLowerCase match {
          case JACCARD => Right(Similarity.Jaccard)
          case HAMMING => Right(Similarity.Hamming)
          case L1      => Right(Similarity.L1)
          case L2      => Right(Similarity.L2)
          case ANGULAR => Right(Similarity.Angular)
          case other   => failTypes(SIMILARITY, Seq(JACCARD, HAMMING, L1, L2, ANGULAR), other)
        }
      } yield sim
    override def apply(a: Similarity): Json = a match {
      case Similarity.Jaccard => JACCARD
      case Similarity.Hamming => HAMMING
      case Similarity.L1      => L1
      case Similarity.L2      => L2
      case Similarity.Angular => ANGULAR
    }
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

  implicit val mappingSparseBool: ESC[Mapping.SparseBool] = ElasticsearchCodec(deriveCodec)
  implicit val mappingDenseFloat: ESC[Mapping.DenseFloat] = ElasticsearchCodec(deriveCodec)
  implicit val mappingSparseIndexed: ESC[Mapping.SparseIndexed] = ElasticsearchCodec(deriveCodec)
  implicit val mappingJaccardLsh: ESC[Mapping.JaccardLsh] = ElasticsearchCodec(deriveCodec)

  implicit val mapping: ESC[Mapping] = new ESC[Mapping] {
    override def apply(t: Mapping): Json = t match {
      case m: Mapping.SparseBool    => JsonObject(TYPE -> EKNN_SPARSE_BOOL_VECTOR) ++ esc.encode(m)
      case m: Mapping.DenseFloat    => JsonObject(TYPE -> EKNN_DENSE_FLOAT_VECTOR) ++ esc.encode(m)
      case m: Mapping.SparseIndexed => JsonObject(TYPE -> EKNN_SPARSE_BOOL_VECTOR, MODEL -> SPARSE_INDEXED) ++ esc.encode(m)
      case m: Mapping.JaccardLsh    => JsonObject(TYPE -> EKNN_SPARSE_BOOL_VECTOR, MODEL -> LSH, SIMILARITY -> JACCARD) ++ esc.encode(m)
    }

    override def apply(c: HCursor): Either[DecodingFailure, Mapping] =
      for {
        typ <- c.downField(TYPE).as[String]
        modelOpt = c.value.findAllByKey(MODEL).headOption.flatMap(_.asString)
        simOpt = c.value.findAllByKey(SIMILARITY).headOption.flatMap(ElasticsearchCodec.decodeJson[Similarity](_).toOption)
        mapping <- (typ, modelOpt, simOpt) match {
          case (EKNN_SPARSE_BOOL_VECTOR, None, None) =>
            esc.decode[Mapping.SparseBool](c)
          case (EKNN_DENSE_FLOAT_VECTOR, None, None) =>
            esc.decode[Mapping.DenseFloat](c)
          case (EKNN_SPARSE_BOOL_VECTOR, Some(SPARSE_INDEXED), None) =>
            esc.decode[Mapping.SparseIndexed](c)
          case (EKNN_SPARSE_BOOL_VECTOR, Some(LSH), Some(Similarity.Jaccard)) =>
            esc.decode[Mapping.JaccardLsh](c)
          case _ =>
            val msg = s"Incompatible $TYPE [$typ], $MODEL [$modelOpt], $SIMILARITY [${simOpt.map(esc.encode(_).noSpaces)}]"
            fail[Mapping](msg)
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
      JsonObject(FIELD -> t.field, VECTOR -> encode(t.vector))
    override def apply(c: HCursor): Either[DecodingFailure, Query.NearestNeighborsQuery] = {
      for {
        field <- c.downField(FIELD).as[String]
        vecJson <- c.downField(VECTOR).as[Json]
        vec <- esc.decodeJson[api.Vec](vecJson)
        qoptsJson <- c.downField(QUERY_OPTIONS).as[Json]
        qopts <- esc.decodeJson[QueryOptions](qoptsJson)
      } yield Query.NearestNeighborsQuery(field, vec, qopts)
    }
  }

}
