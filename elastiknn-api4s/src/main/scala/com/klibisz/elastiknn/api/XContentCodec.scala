package com.klibisz.elastiknn.api

import com.klibisz.elastiknn.ELASTIKNN_NAME
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.{XContentBuilder, XContentParseException, XContentParser}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

trait XContentCodec[T] {
  def buildUnsafe(t: T, x: XContentBuilder): Unit
  def parseUnsafe(p: XContentParser): T
}

object XContentCodec {

  def buildUnsafe[T](t: T, x: XContentBuilder)(implicit c: XContentCodec[T]): Unit =
    c.buildUnsafe(t, x)

  def parseUnsafe[T](p: XContentParser)(implicit c: XContentCodec[T]): T =
    c.parseUnsafe(p)

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

  private def wrongKey(k: String, p: XContentParser): XContentParseException =
    new XContentParseException(s"Expected key [$k] but found [${p.currentName()}]")

  private def unknownValue(s: String): XContentParseException =
    new XContentParseException(s"Unknown value [$s]")

  private def wrongToken(ts: Seq[Token], p: XContentParser): XContentParseException =
    new XContentParseException(s"Expected token to be one of [${ts.mkString(",")}] but found [${p.currentToken()}]")

  implicit val similarity: XContentCodec[Similarity] = new XContentCodec[Similarity] {
    override def buildUnsafe(t: Similarity, x: XContentBuilder): Unit = t match {
      case Similarity.Jaccard => x.value(Keys.JACCARD)
      case Similarity.Hamming => x.value(Keys.HAMMING)
      case Similarity.L1      => x.value(Keys.L1)
      case Similarity.L2      => x.value(Keys.L2)
      case Similarity.Cosine  => x.value(Keys.COSINE)
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
          case _            => throw unknownValue(s1)
        }
      } else throw wrongToken(Seq(Token.VALUE_STRING), p)
  }

  implicit val denseFloatVector: XContentCodec[Vec.DenseFloat] = new XContentCodec[Vec.DenseFloat] {

    private def parseArray(p: XContentParser): Array[Float] = {
      val b = new ArrayBuffer[Float](42) // Ideally we would know the vec dimension up-front.
      while (p.nextToken() == Token.VALUE_NUMBER) {
        b.append(p.numberValue().floatValue())
      }
      b.toArray
    }

    override def buildUnsafe(t: Vec.DenseFloat, x: XContentBuilder): Unit = {
      x.startObject()
      x.array(Keys.VALUES, t.values)
      x.endObject()
    }

    @tailrec
    override def parseUnsafe(p: XContentParser): Vec.DenseFloat =
      if (p.currentToken() == Token.START_OBJECT) {
        p.nextToken()
        if (p.currentName() == Keys.VALUES) {
          p.nextToken()
          parseUnsafe(p)
        } else throw wrongKey(Keys.VALUES, p)
      } else if (p.currentToken() == Token.START_ARRAY) {
        Vec.DenseFloat(parseArray(p))
      } else throw wrongToken(Seq(Token.START_OBJECT, Token.START_ARRAY), p)
  }
}
