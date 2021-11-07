package com.klibisz.elastiknn.api

import com.klibisz.elastiknn.ELASTIKNN_NAME
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent._

import java.io.ByteArrayOutputStream
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

trait XContentCodec[T] {
  def encodeUnsafe(t: T, x: XContentBuilder): Unit
  def decodeUnsafe(p: XContentParser): T
}

trait XContentEncoder[T] {
  def encodeUnsafe(t: T, x: XContentBuilder): Unit
}

trait XContentDecoder[T] {
  def decodeUnsafe(p: XContentParser): T
}

object XContentEncoder {

  implicit def summon[T: XContentCodec]: XContentEncoder[T] = (t: T, x: XContentBuilder) => implicitly[XContentCodec[T]].encodeUnsafe(t, x)

  def encodeUnsafe[T](t: T, b: XContentBuilder)(implicit c: XContentEncoder[T]): Unit =
    c.encodeUnsafe(t, b)

  def encodeUnsafeToByteArray[T](t: T)(implicit c: XContentEncoder[T]): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val b = new XContentBuilder(XContentType.JSON.xContent(), bos)
    encodeUnsafe(t, b)
    b.close()
    bos.toByteArray
  }

  def encodeUnsafeToString[T](t: T)(implicit c: XContentEncoder[T]): String =
    new String(encodeUnsafeToByteArray(t))

  implicit val similarity: XContentEncoder[Similarity] = (t: Similarity, b: XContentBuilder) =>
    t match {
      case Similarity.Jaccard => b.value(XContentKeys.JACCARD)
      case Similarity.Hamming => b.value(XContentKeys.HAMMING)
      case Similarity.L1      => b.value(XContentKeys.L1)
      case Similarity.L2      => b.value(XContentKeys.L2)
      case Similarity.Cosine  => b.value(XContentKeys.COSINE)
    }

  implicit val denseFloatVec: XContentEncoder[Vec.DenseFloat] = (t: Vec.DenseFloat, b: XContentBuilder) => {
    b.startObject()
    b.array(XContentKeys.VALUES, t.values)
    b.endObject()
  }

  implicit val sparseBoolVec: XContentEncoder[Vec.SparseBool] = (t: Vec.SparseBool, b: XContentBuilder) => {
    b.startObject()
    b.field(XContentKeys.TOTAL_INDICES, t.totalIndices)
    b.array(XContentKeys.TRUE_INDICES, t.trueIndices)
    b.endObject()
  }

  implicit val emptyVec: XContentEncoder[Vec.Empty] = (t: Vec.Empty, x: XContentBuilder) => {
    x.startObject()
    x.endObject()
  }

  implicit val indexedVec: XContentEncoder[Vec.Indexed] = (t: Vec.Indexed, x: XContentBuilder) => {
    x.startObject()
    x.field(XContentKeys.INDEX, t.index)
    x.field(XContentKeys.ID, t.id)
    x.field(XContentKeys.FIELD, t.field)
    x.endObject()
  }

  implicit val vec: XContentEncoder[Vec] = (t: Vec, x: XContentBuilder) =>
    t match {
      case dfv: Vec.DenseFloat => denseFloatVec.encodeUnsafe(dfv, x)
      case sbv: Vec.SparseBool => sparseBoolVec.encodeUnsafe(sbv, x)
      case iv: Vec.Indexed     => indexedVec.encodeUnsafe(iv, x)
      case empty: Vec.Empty    => emptyVec.encodeUnsafe(empty, x)
    }

  implicit val mapping: XContentEncoder[Mapping] = (t: Mapping, b: XContentBuilder) => ???

  implicit val nearestNeighborsQuery: XContentEncoder[NearestNeighborsQuery] =
    (t: NearestNeighborsQuery, b: XContentBuilder) => ???

}

object XContentDecoder {

  implicit def summon[T: XContentCodec]: XContentDecoder[T] = (p: XContentParser) => implicitly[XContentCodec[T]].decodeUnsafe(p)

  private val xcJson = XContentType.JSON.xContent()

  private def missingKey(k: String): XContentParseException =
    new XContentParseException(s"Expected to find key [$k] but did not")

  private def unexpectedKey(k: String, p: XContentParser): XContentParseException =
    new XContentParseException(s"Expected key [$k] but found [${p.currentName()}]")

  private def unexpectedValue(s: String): XContentParseException =
    new XContentParseException(s"Unexpected value [$s]")

  private def unexpectedToken(p: XContentParser, ts: Token*): String =
    s"Expected token to be one of [${ts.mkString(",")}] but found [${p.currentToken()}]"

  def decodeUnsafe[T](p: XContentParser)(implicit c: XContentDecoder[T]): T =
    c.decodeUnsafe(p)

  def decodeUnsafeFromMap[T](m: java.util.Map[String, Object])(implicit c: XContentDecoder[T]): T = {
    val bos = new ByteArrayOutputStream()
    val builder = XContentBuilder.builder(xcJson).map(m)
    builder.close()
    val parser = xcJson.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, bos.toByteArray)
    c.decodeUnsafe(parser)
  }

  def decodeUnsafeFromByteArray[T](barr: Array[Byte])(implicit c: XContentDecoder[T]): T = {
    val p = xcJson.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, barr)
    c.decodeUnsafe(p)
  }

  implicit val similarity: XContentDecoder[Similarity] = (p: XContentParser) =>
    if (p.currentToken() == Token.VALUE_STRING) {
      val s1 = p.text()
      val s2 = s1.toLowerCase
      s2 match {
        case XContentKeys.JACCARD => Similarity.Jaccard
        case XContentKeys.HAMMING => Similarity.Hamming
        case XContentKeys.L1      => Similarity.L1
        case XContentKeys.L2      => Similarity.L2
        case XContentKeys.COSINE  => Similarity.Cosine
        case XContentKeys.ANGULAR => Similarity.Cosine
        case _                    => throw unexpectedValue(s1)
      }
    } else throw new XContentParseException(unexpectedToken(p, Token.VALUE_STRING))

  implicit val denseFloatVec: XContentDecoder[Vec.DenseFloat] = new XContentDecoder[Vec.DenseFloat] {
    private def parseArray(p: XContentParser): Array[Float] = {
      val b = new ArrayBuffer[Float](42) // Ideally we would know the vec dimension up-front, but we don't.
      while (p.nextToken() == Token.VALUE_NUMBER) {
        b.append(p.numberValue().floatValue())
      }
      b.toArray
    }
    @tailrec
    override def decodeUnsafe(p: XContentParser): Vec.DenseFloat =
      if (p.currentToken() == Token.START_OBJECT) {
        p.nextToken()
        if (p.currentName() == XContentKeys.VALUES) {
          p.nextToken()
          decodeUnsafe(p) // Parse as an array.
        } else throw unexpectedKey(XContentKeys.VALUES, p)
      } else if (p.currentToken() == Token.START_ARRAY) {
        Vec.DenseFloat(parseArray(p))
      } else throw new XContentParseException(unexpectedToken(p, Token.START_OBJECT, Token.START_ARRAY))
  }

  implicit val sparseBoolVec: XContentDecoder[Vec.SparseBool] = new XContentDecoder[Vec.SparseBool] {
    private def parseArray(p: XContentParser): Array[Int] = {
      val b = new ArrayBuffer[Int](42) // Ideally we would know the vec dimension up-front.
      while (p.nextToken() == Token.VALUE_NUMBER) {
        b.append(p.numberValue().intValue())
      }
      b.toArray
    }
    override def decodeUnsafe(p: XContentParser): Vec.SparseBool = {
      var trueIndices: Array[Int] = None.orNull
      var totalIndices: Int = Int.MinValue
      if (p.currentToken() == Token.START_OBJECT) {
        while (p.nextToken() != Token.END_OBJECT) {
          if (p.currentName() == XContentKeys.TRUE_INDICES) {
            if (p.nextToken() == Token.START_ARRAY) trueIndices = parseArray(p)
            else throw new XContentParseException(unexpectedToken(p, Token.START_ARRAY))
          } else if (p.currentName() == XContentKeys.TOTAL_INDICES) {
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
      if (totalIndices == Int.MinValue) throw missingKey(XContentKeys.TOTAL_INDICES)
      else if (trueIndices == None.orNull) throw missingKey(XContentKeys.TRUE_INDICES)
      else Vec.SparseBool(trueIndices, totalIndices)
    }
  }

  implicit val emptyVec: XContentDecoder[Vec.Empty] = (p: XContentParser) =>
    if (p.currentToken() == Token.START_OBJECT) {
      if (p.nextToken() == Token.END_OBJECT) Vec.Empty()
      else throw new XContentParseException(unexpectedToken(p, Token.END_OBJECT))
    } else throw new XContentParseException(unexpectedToken(p, Token.START_OBJECT))

  implicit val indexedVec: XContentDecoder[Vec.Indexed] = (p: XContentParser) => {
    var index: String = None.orNull
    var id: String = None.orNull
    var field: String = None.orNull
    if (p.currentToken() == Token.START_OBJECT) {
      while (p.nextToken() != Token.END_OBJECT) {
        val name = p.currentName()
        if (p.nextToken() == Token.VALUE_STRING) {
          if (name == XContentKeys.INDEX) index = p.text()
          else if (name == XContentKeys.ID) id = p.text()
          else if (name == XContentKeys.FIELD) field = p.text()
        } else throw new XContentParseException(unexpectedToken(p, Token.VALUE_STRING))
      }
    }
    if (index == None.orNull) throw missingKey(XContentKeys.INDEX)
    else if (id == None.orNull) throw missingKey(XContentKeys.ID)
    else if (field == None.orNull) throw missingKey(XContentKeys.FIELD)
    Vec.Indexed(index, id, field)
  }

  implicit val mapping: XContentDecoder[Mapping] = (p: XContentParser) => ???

  implicit val nearestNeighborsQuery: XContentDecoder[NearestNeighborsQuery] =
    (p: XContentParser) => ???

  def decodeUnsafeVecFromMappingAndMap(mapping: Mapping, map: java.util.Map[String, Object]): Vec = mapping match {
    case _: Mapping.SparseBool     => XContentDecoder.decodeUnsafeFromMap[Vec.SparseBool](map)
    case _: Mapping.JaccardLsh     => XContentDecoder.decodeUnsafeFromMap[Vec.SparseBool](map)
    case _: Mapping.HammingLsh     => XContentDecoder.decodeUnsafeFromMap[Vec.SparseBool](map)
    case _: Mapping.DenseFloat     => XContentDecoder.decodeUnsafeFromMap[Vec.DenseFloat](map)
    case _: Mapping.CosineLsh      => XContentDecoder.decodeUnsafeFromMap[Vec.DenseFloat](map)
    case _: Mapping.L2Lsh          => XContentDecoder.decodeUnsafeFromMap[Vec.DenseFloat](map)
    case _: Mapping.PermutationLsh => XContentDecoder.decodeUnsafeFromMap[Vec.DenseFloat](map)
  }

}

object XContentCodec {

  private def summon[T](implicit e: XContentEncoder[T], d: XContentDecoder[T]): XContentCodec[T] =
    new XContentCodec[T] {
      override def encodeUnsafe(t: T, x: XContentBuilder): Unit = e.encodeUnsafe(t, x)
      override def decodeUnsafe(p: XContentParser): T = d.decodeUnsafe(p)
    }

  // Implementations of XContentCodec for Elastiknn API data types.
  implicit val similarity: XContentCodec[Similarity] = summon
  implicit val denseFloatVec: XContentCodec[Vec.DenseFloat] = summon
  implicit val sparseBoolVec: XContentCodec[Vec.SparseBool] = summon
  implicit val indexedVec: XContentCodec[Vec.Indexed] = summon
  implicit val emptyVec: XContentCodec[Vec.Empty] = summon
  implicit val mapping: XContentCodec[Mapping] = summon
  implicit val nearestNeighborsQuery: XContentCodec[NearestNeighborsQuery] = summon

  // Notably, no codec for [Vec], because the decoder would be a monstrosity to write.
}

private[api] object XContentKeys {
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
