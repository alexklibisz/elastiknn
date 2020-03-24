package com.klibisz.elastiknn.api

import com.klibisz.elastiknn.ELASTIKNN_NAME
import io.circe.{DecodingFailure, Json, JsonObject}

import scala.language.implicitConversions

trait ElasticsearchCodec[T] {
  def encode(t: T): Json
  def decode(j: Json): Either[DecodingFailure, T]
}

object ElasticsearchCodec {

  private val TYPE = "type"

  private type ESC[T] = ElasticsearchCodec[T]

  private def fail[T](msg: String): Either[DecodingFailure, T] = Left(DecodingFailure(msg, List.empty))

  private implicit def jsonObjToJson(jo: JsonObject): Json = Json.fromJsonObject(jo)
  private implicit def intToJson(i: Int): Json = Json.fromInt(i)
  private implicit def strToJson(s: String): Json = Json.fromString(s)

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

  implicit val mapping: ESC[Mapping] = new ESC[Mapping] {

    private val DIMS = "dims"
    private val MOPTS = "model_options"
    private val EKVSBV = s"${ELASTIKNN_NAME}_sparse_bool_vector"
    private val EKVDFV = s"${ELASTIKNN_NAME}_dense_float_vector"

    override def encode(t: Mapping): Json = t match {
      case Mapping.SparseBoolVector(dims, modelOptions) =>
        JsonObject(
          TYPE -> EKVSBV,
          DIMS -> dims,
          MOPTS -> implicitly[ESC[SparseBoolVectorModelOptions]].encode(modelOptions)
        )
      case Mapping.DenseFloatVector(dims, modelOptions) =>
        JsonObject(
          TYPE -> EKVDFV,
          DIMS -> dims,
          MOPTS -> implicitly[ESC[DenseFloatVectorModelOptions]].encode(modelOptions)
        )
    }

    override def decode(j: Json): Either[DecodingFailure, Mapping] = {
      val c = j.hcursor
      for {
        typ <- c.downField(TYPE).as[String]
        dims <- c.downField(DIMS).as[Int]
        moptsJson <- c.downField(MOPTS).as[Json]
        mapping <- typ match {
          case EKVSBV =>
            implicitly[ESC[SparseBoolVectorModelOptions]]
              .decode(moptsJson)
              .map(Mapping.SparseBoolVector(dims, _))
          case EKVDFV =>
            implicitly[ESC[DenseFloatVectorModelOptions]]
              .decode(moptsJson)
              .map(Mapping.DenseFloatVector(dims, _))
          case other => fail(s"Expected $TYPE $EKVSBV or $EKVDFV but got $other")
        }
      } yield mapping
    }

  }

}
