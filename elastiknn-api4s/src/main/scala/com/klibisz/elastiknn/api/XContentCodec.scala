package com.klibisz.elastiknn.api

import com.klibisz.elastiknn.ELASTIKNN_NAME
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent._

import java.io.ByteArrayOutputStream
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

trait XContentCodec[T] {
  def buildUnsafe(t: T, x: XContentBuilder): Unit
  def parseUnsafe(p: XContentParser): T
}

object XContentCodec {

  private val xcJson = XContentType.JSON.xContent()

  def buildUnsafe[T](t: T, b: XContentBuilder)(implicit c: XContentCodec[T]): Unit =
    c.buildUnsafe(t, b)

  def buildUnsafeToByteArray[T](t: T)(implicit c: XContentCodec[T]): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val b = new XContentBuilder(XContentType.JSON.xContent(), bos)
    buildUnsafe(t, b)
    b.close()
    bos.toByteArray
  }

  def buildUnsafeToString[T](t: T)(implicit c: XContentCodec[T]): String =
    new String(buildUnsafeToByteArray(t))

  def parseUnsafe[T](p: XContentParser)(implicit c: XContentCodec[T]): T =
    c.parseUnsafe(p)

  def parseUnsafeFromMap[T](m: java.util.Map[String, Object])(implicit c: XContentCodec[T]): T = {
    val bos = new ByteArrayOutputStream()
    val builder = XContentBuilder.builder(xcJson).map(m)
    builder.close()
    val parser = xcJson.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, bos.toByteArray)
    c.parseUnsafe(parser)
  }

  def parseUnsafeFromByteArray[T](barr: Array[Byte])(implicit c: XContentCodec[T]): T = {
    val p = xcJson.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, barr)
    c.parseUnsafe(p)
  }

  object Keys {
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
    val TYPE = "type"
    val VALUES = "values"
    val VEC = "vec"
  }

  private def unexpectedKey(k: String, p: XContentParser): XContentParseException =
    new XContentParseException(s"Expected key [$k] but found [${p.currentName()}]")

  private def unexpectedValue(s: String): XContentParseException =
    new XContentParseException(s"Unexpected value [$s]")

  private def unexpectedToken(ts: Seq[Token], p: XContentParser): XContentParseException =
    new XContentParseException(s"Expected token to be one of [${ts.mkString(",")}] but found [${p.currentToken()}]")

  implicit val similarity: XContentCodec[Similarity] = new XContentCodec[Similarity] {
    override def buildUnsafe(t: Similarity, b: XContentBuilder): Unit = t match {
      case Similarity.Jaccard => b.value(Keys.JACCARD)
      case Similarity.Hamming => b.value(Keys.HAMMING)
      case Similarity.L1      => b.value(Keys.L1)
      case Similarity.L2      => b.value(Keys.L2)
      case Similarity.Cosine  => b.value(Keys.COSINE)
    }
    override def parseUnsafe(p: XContentParser): Similarity =
      if (p.currentToken() == Token.VALUE_STRING) {
        val s1 = p.text()
        val s2 = s1.toLowerCase
        s2 match {
          case Keys.JACCARD => Similarity.Jaccard
          case Keys.HAMMING => Similarity.Hamming
          case Keys.L1      => Similarity.L1
          case Keys.L2      => Similarity.L2
          case Keys.COSINE  => Similarity.Cosine
          case Keys.ANGULAR => Similarity.Cosine
          case _            => throw unexpectedValue(s1)
        }
      } else throw unexpectedToken(Seq(Token.VALUE_STRING), p)
  }

  implicit val denseFloatVector: XContentCodec[Vec.DenseFloat] = new XContentCodec[Vec.DenseFloat] {

    private def parseArray(p: XContentParser): Array[Float] = {
      val b = new ArrayBuffer[Float](42) // Ideally we would know the vec dimension up-front.
      while (p.nextToken() == Token.VALUE_NUMBER) {
        b.append(p.numberValue().floatValue())
      }
      b.toArray
    }

    override def buildUnsafe(t: Vec.DenseFloat, b: XContentBuilder): Unit = {
      b.startObject()
      b.array(Keys.VALUES, t.values)
      b.endObject()
    }

    @tailrec
    override def parseUnsafe(p: XContentParser): Vec.DenseFloat =
      if (p.currentToken() == Token.START_OBJECT) {
        p.nextToken()
        if (p.currentName() == Keys.VALUES) {
          p.nextToken()
          parseUnsafe(p) // Parse as an array.
        } else throw unexpectedKey(Keys.VALUES, p)
      } else if (p.currentToken() == Token.START_ARRAY) {
        Vec.DenseFloat(parseArray(p))
      } else throw unexpectedToken(Seq(Token.START_OBJECT, Token.START_ARRAY), p)
  }

  implicit val sparseBoolVector: XContentCodec[Vec.SparseBool] = new XContentCodec[Vec.SparseBool] {
    override def buildUnsafe(t: Vec.SparseBool, b: XContentBuilder): Unit = ???
    override def parseUnsafe(p: XContentParser): Vec.SparseBool = ???
  }

  implicit val vec: XContentCodec[Vec] = new XContentCodec[Vec] {
    override def buildUnsafe(t: Vec, x: XContentBuilder): Unit = ???
    override def parseUnsafe(p: XContentParser): Vec = ???
  }

  implicit val mapping: XContentCodec[Mapping] = new XContentCodec[Mapping] {
    override def buildUnsafe(t: Mapping, b: XContentBuilder): Unit = ???
    override def parseUnsafe(p: XContentParser): Mapping = ???
  }

  implicit val nearestNeighborsQuery: XContentCodec[NearestNeighborsQuery] =
    new XContentCodec[NearestNeighborsQuery] {
      override def buildUnsafe(t: NearestNeighborsQuery, b: XContentBuilder): Unit = ???
      override def parseUnsafe(p: XContentParser): NearestNeighborsQuery = ???
    }
}
