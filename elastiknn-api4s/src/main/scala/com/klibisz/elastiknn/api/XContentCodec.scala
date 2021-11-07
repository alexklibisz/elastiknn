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

  // Implementations of XContentCodec for Elastiknn API data types.

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
    val ID = "id"
    val INDEX = "index"
    val JACCARD = "jaccard"
    val L1 = "l1"
    val L2 = "l2"
    val LSH = "lsh"
    val PERMUTATION_LSH = "permutation_lsh"
    val MODEL = "model"
    val QUERY_OPTIONS = "query_options"
    val SIMILARITY = "similarity"
    val TRUE_INDICES = "true_indices"
    val TOTAL_INDICES = "total_indices"
    val TYPE = "type"
    val VALUES = "values"
    val VEC = "vec"
  }

  private def missingKey(k: String): XContentParseException =
    new XContentParseException(s"Expected to find key [$k] but did not")

  private def unexpectedKey(k: String, p: XContentParser): XContentParseException =
    new XContentParseException(s"Expected key [$k] but found [${p.currentName()}]")

  private def unexpectedValue(s: String): XContentParseException =
    new XContentParseException(s"Unexpected value [$s]")

  private def unexpectedToken(p: XContentParser, ts: Token*): String =
    s"Expected token to be one of [${ts.mkString(",")}] but found [${p.currentToken()}]"

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
      } else throw new XContentParseException(unexpectedToken(p, Token.VALUE_STRING))
  }

  implicit val denseFloatVec: XContentCodec[Vec.DenseFloat] = new XContentCodec[Vec.DenseFloat] {

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
      } else throw new XContentParseException(unexpectedToken(p, Token.START_OBJECT, Token.START_ARRAY))
  }

  implicit val sparseBoolVec: XContentCodec[Vec.SparseBool] = new XContentCodec[Vec.SparseBool] {

    private def parseArray(p: XContentParser): Array[Int] = {
      val b = new ArrayBuffer[Int](42) // Ideally we would know the vec dimension up-front.
      while (p.nextToken() == Token.VALUE_NUMBER) {
        b.append(p.numberValue().intValue())
      }
      b.toArray
    }

    override def buildUnsafe(t: Vec.SparseBool, b: XContentBuilder): Unit = {
      b.startObject()
      b.field(Keys.TOTAL_INDICES, t.totalIndices)
      b.array(Keys.TRUE_INDICES, t.trueIndices)
      b.endObject()
    }

    override def parseUnsafe(p: XContentParser): Vec.SparseBool = {
      var trueIndices: Array[Int] = None.orNull
      var totalIndices: Int = Int.MinValue
      if (p.currentToken() == Token.START_OBJECT) {
        while (p.nextToken() != Token.END_OBJECT) {
          if (p.currentName() == Keys.TRUE_INDICES) {
            if (p.nextToken() == Token.START_ARRAY) trueIndices = parseArray(p)
            else throw new XContentParseException(unexpectedToken(p, Token.START_ARRAY))
          } else if (p.currentName() == Keys.TOTAL_INDICES) {
            if (p.nextToken() == Token.VALUE_NUMBER) totalIndices = p.intValue()
            else throw new XContentParseException(unexpectedToken(p, Token.VALUE_NUMBER))
          }
        }
      } else if (p.currentToken() == Token.START_ARRAY) {
        if (p.nextToken() == Token.VALUE_NUMBER) totalIndices = p.intValue()
        else throw new XContentParseException(unexpectedToken(p, Token.VALUE_NUMBER))
        if (p.nextToken() == Token.START_ARRAY) trueIndices = parseArray(p)
        else throw new XContentParseException(unexpectedToken(p, Token.START_ARRAY))
      } else throw new XContentParseException(unexpectedToken(p, Token.START_OBJECT, Token.START_ARRAY))
      if (totalIndices == Int.MinValue) throw missingKey(Keys.TOTAL_INDICES)
      else if (trueIndices == None.orNull) throw missingKey(Keys.TRUE_INDICES)
      else Vec.SparseBool(trueIndices, totalIndices)
    }
  }

  implicit val emptyVec: XContentCodec[Vec.Empty] = new XContentCodec[Vec.Empty] {
    override def buildUnsafe(t: Vec.Empty, x: XContentBuilder): Unit = {
      x.startObject()
      x.endObject()
    }
    override def parseUnsafe(p: XContentParser): Vec.Empty =
      if (p.currentToken() == Token.START_OBJECT) {
        if (p.nextToken() == Token.END_OBJECT) Vec.Empty()
        else throw new XContentParseException(unexpectedToken(p, Token.END_OBJECT))
      } else throw new XContentParseException(unexpectedToken(p, Token.START_OBJECT))
  }

  implicit val indexedVec: XContentCodec[Vec.Indexed] = new XContentCodec[Vec.Indexed] {
    override def buildUnsafe(t: Vec.Indexed, x: XContentBuilder): Unit = {
      x.startObject()
      x.field(Keys.INDEX, t.index)
      x.field(Keys.ID, t.id)
      x.field(Keys.FIELD, t.field)
      x.endObject()
    }

    override def parseUnsafe(p: XContentParser): Vec.Indexed = {
      var index: String = None.orNull
      var id: String = None.orNull
      var field: String = None.orNull
      if (p.currentToken() == Token.START_OBJECT) {
        while (p.nextToken() != Token.END_OBJECT) {
          val name = p.currentName()
          if (p.nextToken() == Token.VALUE_STRING) {
            if (name == Keys.INDEX) index = p.text()
            else if (name == Keys.ID) id = p.text()
            else if (name == Keys.FIELD) field = p.text()
          } else throw new XContentParseException(unexpectedToken(p, Token.VALUE_STRING))
        }
      }
      if (index == None.orNull) throw missingKey(Keys.INDEX)
      else if (id == None.orNull) throw missingKey(Keys.ID)
      else if (field == None.orNull) throw missingKey(Keys.FIELD)
      Vec.Indexed(index, id, field)
    }
  }

  implicit val vec: XContentCodec[Vec] = new XContentCodec[Vec] {
    override def buildUnsafe(t: Vec, x: XContentBuilder): Unit = t match {
      case dfv: Vec.DenseFloat => denseFloatVec.buildUnsafe(dfv, x)
      case sbv: Vec.SparseBool => sparseBoolVec.buildUnsafe(sbv, x)
      case iv: Vec.Indexed     => indexedVec.buildUnsafe(iv, x)
      case empty: Vec.Empty    => emptyVec.buildUnsafe(empty, x)
    }

    // {"true_indices": [ ... ], "total_indices": 99 }
    // {"values": [ ... ] }
    // [ ... ]
    // {"index": "...", "field": "...", "id": "..." }
    // { }

    override def parseUnsafe(p: XContentParser): Vec = {
      ???
    }
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
