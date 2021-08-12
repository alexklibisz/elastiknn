package com.klibisz.elastiknn.api

import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import io.circe
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.generic.semiauto.deriveCodec

import scala.language.implicitConversions

/**
  * Typeclass for handling idiomatic elasticsearch JSON using Circe.
  * @tparam A
  */
trait ElasticsearchCodec[A] extends Codec[A]

private object Keys {
  val ANGULAR = "angular"
  val COSINE = "cosine"
  val DIMS = "dims"
  val EKNN_DENSE_FLOAT_VECTOR = s"${ELASTIKNN_NAME}_dense_float_vector"
  val EKNN_SPARSE_BOOL_VECTOR = s"${ELASTIKNN_NAME}_sparse_bool_vector"
  val MODEL_OPTIONS = "model_options"
  val EXACT = "exact"
  val FIELD = "field"
  val HAMMING = "hamming"
  val INDEX = "index"
  val JACCARD = "jaccard"
  val L1 = "l1"
  val L2 = "l2"
  val LSH = "lsh"
  val PERMUTATION_LSH = "permutation_lsh"
  val MODEL = "model"
  val QUERY_OPTIONS = "query_options"
  val SIMILARITY = "similarity"
  val SPARSE_INDEXED = "sparse_indexed"
  val TYPE = "type"
  val VEC = "vec"
}

/**
  * Implementations of the ElasticsearchCodec for all members of the api package.
  * If you think this is a lot of boilerplate you should see the Json parsing in Elasticsearch.
  */
object ElasticsearchCodec { esc =>

  import Keys._

  private def apply[A](codec: Codec[A]): ElasticsearchCodec[A] =
    new ESC[A] {
      override def apply(c: HCursor): Result[A] = codec(c)
      override def apply(a: A): Json = codec(a)
    }

  private def apply[A](encoder: Encoder[A], decoder: Decoder[A]): ESC[A] = apply(Codec.from(decoder, encoder))

  private type ESC[T] = ElasticsearchCodec[T]
  private type JO = JsonObject

  private def fail[T](msg: String): Either[DecodingFailure, T] = Left(DecodingFailure(msg, List.empty))
  private def failTypes[T](field: String, good: Seq[String], bad: String): Either[DecodingFailure, T] =
    fail(s"Expected field $field to be one of (${good.mkString(", ")}) but got $bad")

  private implicit def jsonObjToJson(jo: JsonObject): Json = Json.fromJsonObject(jo)
  private implicit def intToJson(i: Int): Json = Json.fromInt(i)
  private implicit def strToJson(s: String): Json = Json.fromString(s)
  private implicit class EitherSyntax[+L1, +R1](either: Either[L1, R1]) {
    def orElse[L2 >: L1, R2 >: R1](other: => Either[L2, R2]): Either[L2, R2] = if (either.isRight) either else other
  }
  private implicit class JsonSyntax(j: Json) {
    def ++(other: Json): Json = j.deepMerge(other)
  }
  private implicit class JsonObjectSyntax(j: JsonObject) {
    def ++(other: Json): Json = j.deepMerge(other)
  }

  def encode[T: ElasticsearchCodec](t: T): Json = implicitly[ElasticsearchCodec[T]].apply(t)
  def nospaces[T: ElasticsearchCodec](t: T): String = encode(t).noSpaces
  def decode[T: ElasticsearchCodec](c: HCursor): Either[DecodingFailure, T] = implicitly[ElasticsearchCodec[T]].apply(c)
  def decodeJson[T: ElasticsearchCodec](j: Json): Either[DecodingFailure, T] = implicitly[ElasticsearchCodec[T]].decodeJson(j)
  def parse(s: String): Either[circe.Error, Json] = io.circe.parser.parse(s)

  // Danger zone.
  def decodeGet[T: ElasticsearchCodec](c: HCursor): T = decode[T](c).toTry.get
  def decodeJsonGet[T: ElasticsearchCodec](j: Json): T = decodeJson[T](j).toTry.get
  def parseGet[T: ElasticsearchCodec](s: String): Json = parse(s).toTry.get

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
          case COSINE  => Right(Similarity.Cosine)
          case ANGULAR => Right(Similarity.Cosine)
          case other   => failTypes(SIMILARITY, Seq(JACCARD, HAMMING, L1, L2, ANGULAR, COSINE), other)
        }
      } yield sim
    override def apply(a: Similarity): Json =
      a match {
        case Similarity.Jaccard => JACCARD
        case Similarity.Hamming => HAMMING
        case Similarity.L1      => L1
        case Similarity.L2      => L2
        case Similarity.Cosine  => COSINE
      }
  }

  implicit val denseFloatVector: ESC[Vec.DenseFloat] = {
    val derivedCodec: ESC[Vec.DenseFloat] = ElasticsearchCodec(deriveCodec)
    val shortHandDecoder = new Decoder[Vec.DenseFloat] {
      override def apply(c: HCursor): Result[Vec.DenseFloat] = c.as[Array[Float]].map(Vec.DenseFloat(_))
    }
    new ESC[Vec.DenseFloat] {
      override def apply(c: HCursor): Result[Vec.DenseFloat] = derivedCodec(c).orElse(shortHandDecoder(c))
      override def apply(a: Vec.DenseFloat): Json = derivedCodec(a)
    }
  }
  implicit val sparseBoolVector: ESC[Vec.SparseBool] = {
    implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames
    val derivedCodec: ESC[Vec.SparseBool] = ElasticsearchCodec(deriveConfiguredCodec)
    // Shorthand for a SparseBool(Array(i1, i2, i3), n) is [[i1,i2,i3], n].
    val shortHandDecoder = new Decoder[Vec.SparseBool] {
      override def apply(c: HCursor): Result[Vec.SparseBool] =
        c.value.as[(Array[Int], Int)].map {
          case (trueIndices, totalIndices) => Vec.SparseBool(trueIndices, totalIndices)
        }
    }
    new ESC[Vec.SparseBool] {
      override def apply(a: Vec.SparseBool): Json = derivedCodec(a)
      override def apply(c: HCursor): Result[Vec.SparseBool] = derivedCodec(c).orElse(shortHandDecoder(c))
    }
  }
  implicit val indexedVector: ESC[Vec.Indexed] = ElasticsearchCodec(deriveCodec)
  implicit val emptyVec: ESC[Vec.Empty] = {
    implicit val cfg: Configuration = Configuration.default.withStrictDecoding
    ElasticsearchCodec(deriveConfiguredCodec)
  }

  implicit val vec: ESC[api.Vec] = new ESC[api.Vec] {
    override def apply(t: Vec): Json =
      t match {
        case ixv: Vec.Indexed    => encode(ixv)
        case sbv: Vec.SparseBool => encode(sbv)
        case dfv: Vec.DenseFloat => encode(dfv)
        case emp: Vec.Empty      => encode(emp)
      }
    // TODO: Compare performance of .orElse to alternatives that just check for specific json keys.
    override def apply(c: HCursor): Either[DecodingFailure, Vec] =
      sparseBoolVector(c).orElse(denseFloatVector(c)).orElse(indexedVector(c)).orElse(emptyVec(c))
  }

  implicit val mappingSparseBool: ESC[Mapping.SparseBool] = ElasticsearchCodec(deriveCodec)
  implicit val mappingDenseFloat: ESC[Mapping.DenseFloat] = ElasticsearchCodec(deriveCodec)
  implicit val mappingSparseIndexed: ESC[Mapping.SparseIndexed] = ElasticsearchCodec(deriveCodec)
  implicit val mappingJaccardLsh: ESC[Mapping.JaccardLsh] = ElasticsearchCodec(deriveCodec)
  implicit val mappingHammingLsh: ESC[Mapping.HammingLsh] = ElasticsearchCodec(deriveCodec)
  implicit val mappingCosineLsh: ESC[Mapping.CosineLsh] = ElasticsearchCodec(deriveCodec)
  implicit val mappingL2Lsh: ESC[Mapping.L2Lsh] = ElasticsearchCodec(deriveCodec)
  implicit val mappingPermutationLsh: ESC[Mapping.PermutationLsh] = ElasticsearchCodec(deriveCodec)

  implicit val mapping: ESC[Mapping] = new ESC[Mapping] {
    override def apply(t: Mapping): Json =
      t match {
        case m: Mapping.SparseBool => JsonObject(TYPE -> EKNN_SPARSE_BOOL_VECTOR, ELASTIKNN_NAME -> esc.encode(m))
        case m: Mapping.DenseFloat => JsonObject(TYPE -> EKNN_DENSE_FLOAT_VECTOR, ELASTIKNN_NAME -> esc.encode(m))
        case m: Mapping.SparseIndexed =>
          JsonObject(TYPE -> EKNN_SPARSE_BOOL_VECTOR, ELASTIKNN_NAME -> (esc.encode(m) ++ JsonObject(MODEL -> SPARSE_INDEXED)))
        case m: Mapping.JaccardLsh =>
          JsonObject(TYPE -> EKNN_SPARSE_BOOL_VECTOR, ELASTIKNN_NAME -> (esc.encode(m) ++ JsonObject(MODEL -> LSH, SIMILARITY -> JACCARD)))
        case m: Mapping.HammingLsh =>
          JsonObject(TYPE -> EKNN_SPARSE_BOOL_VECTOR, ELASTIKNN_NAME -> (esc.encode(m) ++ JsonObject(MODEL -> LSH, SIMILARITY -> HAMMING)))
        case m: Mapping.CosineLsh =>
          JsonObject(TYPE -> EKNN_DENSE_FLOAT_VECTOR, ELASTIKNN_NAME -> (esc.encode(m) ++ JsonObject(MODEL -> LSH, SIMILARITY -> ANGULAR)))
        case m: Mapping.L2Lsh =>
          JsonObject(TYPE -> EKNN_DENSE_FLOAT_VECTOR, ELASTIKNN_NAME -> (esc.encode(m) ++ JsonObject(MODEL -> LSH, SIMILARITY -> L2)))
        case m: Mapping.PermutationLsh =>
          JsonObject(TYPE -> EKNN_DENSE_FLOAT_VECTOR, ELASTIKNN_NAME -> (esc.encode(m) ++ JsonObject(MODEL -> PERMUTATION_LSH)))
      }

    override def apply(c: HCursor): Either[DecodingFailure, Mapping] =
      for {
        typ <- c.downField(TYPE).as[String]
        c <- c.downField(ELASTIKNN_NAME).as[Json].map(_.hcursor)
        modelOpt = c.value.findAllByKey(MODEL).headOption.flatMap(_.asString)
        simOpt = c.value.findAllByKey(SIMILARITY).headOption.flatMap(esc.decodeJson[Similarity](_).toOption)
        mapping <- (typ, modelOpt, simOpt) match {
          case (EKNN_SPARSE_BOOL_VECTOR, None, None) =>
            esc.decode[Mapping.SparseBool](c)
          case (EKNN_DENSE_FLOAT_VECTOR, None, None) =>
            esc.decode[Mapping.DenseFloat](c)
          case (EKNN_SPARSE_BOOL_VECTOR, Some(SPARSE_INDEXED), None) =>
            esc.decode[Mapping.SparseIndexed](c)
          case (EKNN_SPARSE_BOOL_VECTOR, Some(LSH), Some(Similarity.Jaccard)) =>
            esc.decode[Mapping.JaccardLsh](c)
          case (EKNN_SPARSE_BOOL_VECTOR, Some(LSH), Some(Similarity.Hamming)) =>
            esc.decode[Mapping.HammingLsh](c)
          case (EKNN_DENSE_FLOAT_VECTOR, Some(LSH), Some(Similarity.Cosine)) =>
            esc.decode[Mapping.CosineLsh](c)
          case (EKNN_DENSE_FLOAT_VECTOR, Some(LSH), Some(Similarity.L2)) =>
            esc.decode[Mapping.L2Lsh](c)
          case (EKNN_DENSE_FLOAT_VECTOR, Some(PERMUTATION_LSH), _) => esc.decode[Mapping.PermutationLsh](c)
          case _ =>
            val msg = s"Incompatible $TYPE [$typ], $MODEL [$modelOpt], $SIMILARITY [$simOpt}]"
            fail[Mapping](msg)
        }
      } yield mapping
  }

  implicit val queryExact: ESC[NearestNeighborsQuery.Exact] = ElasticsearchCodec(deriveCodec)
  implicit val querySparseIndexed: ESC[NearestNeighborsQuery.SparseIndexed] = ElasticsearchCodec(deriveCodec)
  implicit val queryJaccardLsh: ESC[NearestNeighborsQuery.JaccardLsh] = {
    implicit val cfg: Configuration = Configuration.default.withDefaults
    ElasticsearchCodec(deriveConfiguredCodec)
  }
  implicit val queryHammingLsh: ESC[NearestNeighborsQuery.HammingLsh] = {
    implicit val cfg: Configuration = Configuration.default.withDefaults
    ElasticsearchCodec(deriveConfiguredCodec)
  }
  implicit val queryAngularLsh: ESC[NearestNeighborsQuery.CosineLsh] = {
    implicit val cfg: Configuration = Configuration.default.withDefaults
    ElasticsearchCodec(deriveConfiguredCodec)
  }
  implicit val queryL2Lsh: ESC[NearestNeighborsQuery.L2Lsh] = {
    implicit val cfg: Configuration = Configuration.default.withDefaults
    ElasticsearchCodec(deriveConfiguredCodec)
  }
  implicit val queryPermutationLsh: ESC[NearestNeighborsQuery.PermutationLsh] = {
    implicit val cfg: Configuration = Configuration.default.withDefaults
    ElasticsearchCodec(deriveConfiguredCodec)
  }

  implicit val nearestNeighborsQuery: ESC[NearestNeighborsQuery] = new ESC[NearestNeighborsQuery] {
    override def apply(a: NearestNeighborsQuery): Json = {
      val default = JsonObject(FIELD -> a.field, VEC -> esc.encode(a.vec), SIMILARITY -> esc.encode(a.similarity))
      a match {
        case q: NearestNeighborsQuery.Exact          => JsonObject(MODEL -> EXACT) ++ (default ++ esc.encode(q))
        case q: NearestNeighborsQuery.SparseIndexed  => JsonObject(MODEL -> SPARSE_INDEXED) ++ (default ++ esc.encode(q))
        case q: NearestNeighborsQuery.JaccardLsh     => JsonObject(MODEL -> LSH) ++ (default ++ esc.encode(q))
        case q: NearestNeighborsQuery.HammingLsh     => JsonObject(MODEL -> LSH) ++ (default ++ esc.encode(q))
        case q: NearestNeighborsQuery.CosineLsh      => JsonObject(MODEL -> LSH) ++ (default ++ esc.encode(q))
        case q: NearestNeighborsQuery.L2Lsh          => JsonObject(MODEL -> LSH) ++ (default ++ esc.encode(q))
        case q: NearestNeighborsQuery.PermutationLsh => JsonObject(MODEL -> PERMUTATION_LSH) ++ (default ++ esc.encode(q))
      }
    }
    override def apply(c: HCursor): Result[NearestNeighborsQuery] =
      for {
        model <- c.downField(MODEL).as[String]
        sim <- c.downField(SIMILARITY).as[Json].flatMap(esc.decodeJson[Similarity])
        nnq <- model match {
          case EXACT           => esc.decode[NearestNeighborsQuery.Exact](c)
          case SPARSE_INDEXED  => esc.decode[NearestNeighborsQuery.SparseIndexed](c)
          case PERMUTATION_LSH => esc.decode[NearestNeighborsQuery.PermutationLsh](c)
          case LSH =>
            sim match {
              case Similarity.Jaccard => esc.decode[NearestNeighborsQuery.JaccardLsh](c)
              case Similarity.Hamming => esc.decode[NearestNeighborsQuery.HammingLsh](c)
              case Similarity.Cosine  => esc.decode[NearestNeighborsQuery.CosineLsh](c)
              case Similarity.L2      => esc.decode[NearestNeighborsQuery.L2Lsh](c)
              case other              => fail(s"$SIMILARITY [$other] is not compatible with $MODEL [$LSH]")
            }
          case other => failTypes(MODEL, Seq(EXACT, SPARSE_INDEXED, LSH), other)
        }
      } yield nnq
  }

}
