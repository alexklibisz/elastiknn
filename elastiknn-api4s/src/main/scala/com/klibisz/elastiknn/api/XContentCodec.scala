package com.klibisz.elastiknn.api

import com.klibisz.elastiknn.ELASTIKNN_NAME
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent._

import java.io.ByteArrayOutputStream
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

object XContentCodec {

  trait Encoder[T] {
    def encodeUnsafe(t: T, x: XContentBuilder): Unit
  }

  trait Decoder[T] {
    def decodeUnsafe(p: XContentParser): T
  }

  private val xcJson = XContentType.JSON.xContent()

  def encodeUnsafe[T](t: T, b: XContentBuilder)(implicit c: Encoder[T]): Unit =
    c.encodeUnsafe(t, b)

  def encodeUnsafeToByteArray[T](t: T)(implicit c: Encoder[T]): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val b = new XContentBuilder(XContentType.JSON.xContent(), bos)
    encodeUnsafe(t, b)
    b.close()
    bos.toByteArray
  }

  def encodeUnsafeToString[T](t: T)(implicit c: Encoder[T]): String =
    new String(encodeUnsafeToByteArray(t))

  def decodeUnsafe[T](p: XContentParser)(implicit c: Decoder[T]): T =
    c.decodeUnsafe(p)

  def decodeUnsafeFromMap[T](m: java.util.Map[String, Object])(implicit c: Decoder[T]): T = {
    val bos = new ByteArrayOutputStream()
    val builder = XContentBuilder.builder(xcJson).map(m)
    builder.close()
    val parser = xcJson.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, bos.toByteArray)
    c.decodeUnsafe(parser)
  }

  def decodeUnsafeFromByteArray[T](barr: Array[Byte])(implicit c: Decoder[T]): T = {
    val p = xcJson.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, barr)
    c.decodeUnsafe(p)
  }

  object Encoder {

    implicit val similarity: Encoder[Similarity] = (t: Similarity, b: XContentBuilder) =>
      t match {
        case Similarity.Jaccard => b.value(XContentKeys.JACCARD)
        case Similarity.Hamming => b.value(XContentKeys.HAMMING)
        case Similarity.L1      => b.value(XContentKeys.L1)
        case Similarity.L2      => b.value(XContentKeys.L2)
        case Similarity.Cosine  => b.value(XContentKeys.COSINE)
      }

    implicit val denseFloatVec: Encoder[Vec.DenseFloat] = (t: Vec.DenseFloat, b: XContentBuilder) => {
      b.startObject()
      b.array(XContentKeys.VALUES, t.values)
      b.endObject()
    }

    implicit val sparseBoolVec: Encoder[Vec.SparseBool] = (t: Vec.SparseBool, b: XContentBuilder) => {
      b.startObject()
      b.field(XContentKeys.TOTAL_INDICES, t.totalIndices)
      b.array(XContentKeys.TRUE_INDICES, t.trueIndices)
      b.endObject()
    }

    implicit val emptyVec: Encoder[Vec.Empty] = (t: Vec.Empty, x: XContentBuilder) => {
      x.startObject()
      x.endObject()
    }

    implicit val indexedVec: Encoder[Vec.Indexed] = (t: Vec.Indexed, x: XContentBuilder) => {
      x.startObject()
      x.field(XContentKeys.INDEX, t.index)
      x.field(XContentKeys.ID, t.id)
      x.field(XContentKeys.FIELD, t.field)
      x.endObject()
    }

    implicit val vec: Encoder[Vec] = (t: Vec, x: XContentBuilder) =>
      t match {
        case dfv: Vec.DenseFloat => denseFloatVec.encodeUnsafe(dfv, x)
        case sbv: Vec.SparseBool => sparseBoolVec.encodeUnsafe(sbv, x)
        case iv: Vec.Indexed     => indexedVec.encodeUnsafe(iv, x)
        case empty: Vec.Empty    => emptyVec.encodeUnsafe(empty, x)
      }

    implicit val sparseBoolMapping: Encoder[Mapping.SparseBool] = (m: Mapping.SparseBool, x: XContentBuilder) => {
      x.startObject()
      x.field(XContentKeys.TYPE, XContentKeys.EKNN_SPARSE_BOOL_VECTOR)
      x.startObject(XContentKeys.ELASTIKNN)
      x.field(XContentKeys.MODEL, XContentKeys.EXACT)
      x.field(XContentKeys.DIMS, m.dims)
      x.endObject()
      x.endObject()
    }

    implicit val jaccardLshMapping: Encoder[Mapping.JaccardLsh] = (m: Mapping.JaccardLsh, x: XContentBuilder) => {
      x.startObject()
      x.field(XContentKeys.TYPE, XContentKeys.EKNN_SPARSE_BOOL_VECTOR)
      x.startObject(XContentKeys.ELASTIKNN)
      x.field(XContentKeys.MODEL, XContentKeys.LSH)
      x.field(XContentKeys.SIMILARITY, XContentKeys.JACCARD)
      x.field(XContentKeys.DIMS, m.dims)
      x.field(XContentKeys.LSH_L, m.L)
      x.field(XContentKeys.LSH_K, m.k)
      x.endObject()
      x.endObject()
    }

    implicit val hammingLshMapping: Encoder[Mapping.HammingLsh] = (m: Mapping.HammingLsh, x: XContentBuilder) => {
      x.startObject()
      x.field(XContentKeys.TYPE, XContentKeys.EKNN_SPARSE_BOOL_VECTOR)
      x.startObject(XContentKeys.ELASTIKNN)
      x.field(XContentKeys.MODEL, XContentKeys.LSH)
      x.field(XContentKeys.SIMILARITY, XContentKeys.HAMMING)
      x.field(XContentKeys.DIMS, m.dims)
      x.field(XContentKeys.LSH_L, m.L)
      x.field(XContentKeys.LSH_K, m.k)
      x.endObject()
      x.endObject()
    }

    implicit val denseFloatMapping: Encoder[Mapping.DenseFloat] = (m: Mapping.DenseFloat, x: XContentBuilder) => {
      x.startObject()
      x.field(XContentKeys.TYPE, XContentKeys.EKNN_DENSE_FLOAT_VECTOR)
      x.startObject(XContentKeys.ELASTIKNN)
      x.field(XContentKeys.MODEL, XContentKeys.EXACT)
      x.field(XContentKeys.DIMS, m.dims)
      x.endObject()
      x.endObject()
    }

    implicit val cosineLshMapping: Encoder[Mapping.CosineLsh] = (m: Mapping.CosineLsh, x: XContentBuilder) => {
      x.startObject()
      x.field(XContentKeys.TYPE, XContentKeys.EKNN_DENSE_FLOAT_VECTOR)
      x.startObject(XContentKeys.ELASTIKNN)
      x.field(XContentKeys.MODEL, XContentKeys.LSH)
      x.field(XContentKeys.SIMILARITY, XContentKeys.COSINE)
      x.field(XContentKeys.DIMS, m.dims)
      x.field(XContentKeys.LSH_L, m.L)
      x.field(XContentKeys.LSH_K, m.k)
      x.endObject()
      x.endObject()
    }

    implicit val l2LshMapping: Encoder[Mapping.L2Lsh] = (m: Mapping.L2Lsh, x: XContentBuilder) => {
      x.startObject()
      x.field(XContentKeys.TYPE, XContentKeys.EKNN_DENSE_FLOAT_VECTOR)
      x.startObject(XContentKeys.ELASTIKNN)
      x.field(XContentKeys.MODEL, XContentKeys.LSH)
      x.field(XContentKeys.SIMILARITY, XContentKeys.L2)
      x.field(XContentKeys.DIMS, m.dims)
      x.field(XContentKeys.LSH_L, m.L)
      x.field(XContentKeys.LSH_K, m.k)
      x.field(XContentKeys.LSH_W, m.w)
      x.endObject()
      x.endObject()
    }

    implicit val permutationLshMapping: Encoder[Mapping.PermutationLsh] = (m: Mapping.PermutationLsh, x: XContentBuilder) => {
      x.startObject()
      x.field(XContentKeys.TYPE, XContentKeys.EKNN_DENSE_FLOAT_VECTOR)
      x.startObject(XContentKeys.ELASTIKNN)
      x.field(XContentKeys.MODEL, XContentKeys.PERMUTATION_LSH)
      x.field(XContentKeys.DIMS, m.dims)
      x.field(XContentKeys.LSH_K, m.k)
      x.field(XContentKeys.REPEATING, m.repeating)
      x.endObject()
      x.endObject()
    }

    implicit val mapping: Encoder[Mapping] = (m: Mapping, b: XContentBuilder) =>
      m match {
        case m: Mapping.SparseBool     => sparseBoolMapping.encodeUnsafe(m, b)
        case m: Mapping.JaccardLsh     => jaccardLshMapping.encodeUnsafe(m, b)
        case m: Mapping.HammingLsh     => hammingLshMapping.encodeUnsafe(m, b)
        case m: Mapping.DenseFloat     => denseFloatMapping.encodeUnsafe(m, b)
        case m: Mapping.CosineLsh      => cosineLshMapping.encodeUnsafe(m, b)
        case m: Mapping.L2Lsh          => l2LshMapping.encodeUnsafe(m, b)
        case m: Mapping.PermutationLsh => permutationLshMapping.encodeUnsafe(m, b)
      }

    implicit val nearestNeighborsQuery: Encoder[NearestNeighborsQuery] =
      (t: NearestNeighborsQuery, b: XContentBuilder) => ???

  }

  object Decoder {

    private def missingKey(k: String): String =
      s"Expected to find key [$k] but did not"

    private def unexpectedKey(k: String, p: XContentParser): String =
      s"Expected key [$k] but found [${p.currentName()}]"

    private def unexpectedValue(s: String): String =
      s"Unexpected value [$s]"

    private def unexpectedToken(p: XContentParser, ts: Token*): String =
      s"Expected token to be one of [${ts.mkString(",")}] but found [${p.currentToken()}]"

//    private class Parser(p: XContentParser) {
//      def advance(body: => Unit): Unit = {
//        p.nextToken()
//        body
//      }
//      def expectToken(t: Token)(body: => Unit): Unit =
//        if (p.currentToken() == t) body else throw new XContentParseException(unexpectedToken(p, t))
//
//      def readString[T](body: String => T): Unit =
//        if (p.currentToken() == Token.VALUE_STRING) body(p.text())
//        else throw new XContentParseException(unexpectedToken(p, Token.VALUE_STRING))
//
//    }
//
//    private class MonadicParser

    implicit val similarity: Decoder[Similarity] = (p: XContentParser) => {
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
          case _                    => throw new XContentParseException(unexpectedValue(s1))
        }
      } else throw new XContentParseException(unexpectedToken(p, Token.VALUE_STRING))
    }

    implicit val denseFloatVec: Decoder[Vec.DenseFloat] = new Decoder[Vec.DenseFloat] {
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
          } else throw new XContentParseException(unexpectedKey(XContentKeys.VALUES, p))
        } else if (p.currentToken() == Token.START_ARRAY) {
          Vec.DenseFloat(parseArray(p))
        } else throw new XContentParseException(unexpectedToken(p, Token.START_OBJECT, Token.START_ARRAY))
    }

    implicit val sparseBoolVec: Decoder[Vec.SparseBool] = new Decoder[Vec.SparseBool] {
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
        if (totalIndices == Int.MinValue) throw new XContentParseException(missingKey(XContentKeys.TOTAL_INDICES))
        else if (trueIndices == None.orNull) throw new XContentParseException(missingKey(XContentKeys.TRUE_INDICES))
        else Vec.SparseBool(trueIndices, totalIndices)
      }
    }

    implicit val emptyVec: Decoder[Vec.Empty] = (p: XContentParser) =>
      if (p.currentToken() == Token.START_OBJECT) {
        if (p.nextToken() == Token.END_OBJECT) Vec.Empty()
        else throw new XContentParseException(unexpectedToken(p, Token.END_OBJECT))
      } else throw new XContentParseException(unexpectedToken(p, Token.START_OBJECT))

    implicit val indexedVec: Decoder[Vec.Indexed] = (p: XContentParser) => {
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
      } else throw new XContentParseException(unexpectedToken(p, Token.START_OBJECT))
      if (index == None.orNull) throw new XContentParseException(missingKey(XContentKeys.INDEX))
      else if (id == None.orNull) throw new XContentParseException(missingKey(XContentKeys.ID))
      else if (field == None.orNull) throw new XContentParseException(missingKey(XContentKeys.FIELD))
      Vec.Indexed(index, id, field)
    }

    implicit val mapping: Decoder[Mapping] = (p: XContentParser) => {
      var dims: Int = Int.MinValue
      var model: String = None.orNull
      var similarity: String = None.orNull
      var L: Int = Int.MinValue
      var k: Int = Int.MinValue
      var w: Int = Int.MinValue
      var repeating: Boolean = false
      var typ: String = None.orNull

      if (p.currentToken() == Token.START_OBJECT) {
        while (p.nextToken() != Token.END_OBJECT) {
          if (p.currentToken() == Token.FIELD_NAME && p.currentName() == XContentKeys.TYPE) {
            if (p.nextToken() == Token.VALUE_STRING) typ = p.text()
            else throw new XContentParseException(unexpectedToken(p, Token.VALUE_STRING))
          } else if (p.currentToken() == Token.START_OBJECT) {
            while (p.nextToken() != Token.END_OBJECT) {
              if (p.currentToken() == Token.FIELD_NAME) {
                if (p.currentName() == XContentKeys.DIMS) {
                  if (p.nextToken() == Token.VALUE_NUMBER) dims = p.intValue()
                  else throw new XContentParseException(unexpectedToken(p, Token.VALUE_NUMBER))
                } else if (p.currentName() == XContentKeys.MODEL) {
                  if (p.nextToken() == Token.VALUE_STRING) {
                    ???
                  }
                }
              } else throw new XContentParseException(unexpectedToken(p, Token.FIELD_NAME))

            }
          } else throw new XContentParseException(unexpectedToken(p, Token.VALUE_STRING, Token.START_OBJECT))
        }
      } else throw new XContentParseException(unexpectedToken(p, Token.START_OBJECT))

      ???
    }

    implicit val nearestNeighborsQuery: Decoder[NearestNeighborsQuery] =
      (p: XContentParser) => ???

    def decodeUnsafeVecFromMappingAndMap(mapping: Mapping, map: java.util.Map[String, Object]): Vec = mapping match {
      case _: Mapping.SparseBool     => decodeUnsafeFromMap[Vec.SparseBool](map)
      case _: Mapping.JaccardLsh     => decodeUnsafeFromMap[Vec.SparseBool](map)
      case _: Mapping.HammingLsh     => decodeUnsafeFromMap[Vec.SparseBool](map)
      case _: Mapping.DenseFloat     => decodeUnsafeFromMap[Vec.DenseFloat](map)
      case _: Mapping.CosineLsh      => decodeUnsafeFromMap[Vec.DenseFloat](map)
      case _: Mapping.L2Lsh          => decodeUnsafeFromMap[Vec.DenseFloat](map)
      case _: Mapping.PermutationLsh => decodeUnsafeFromMap[Vec.DenseFloat](map)
    }
  }

  private object XContentKeys {
    val ANGULAR = "angular"
    val COSINE = "cosine"
    val DIMS = "dims"
    val ELASTIKNN = "elastiknn"
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
    val LSH_L = "L"
    val LSH_K = "k"
    val LSH_W = "w"
    val PERMUTATION_LSH = "permutation_lsh"
    val MODEL = "model"
    val QUERY_OPTIONS = "query_options"
    val REPEATING = "repeating"
    val SIMILARITY = "similarity"
    val TRUE_INDICES = "true_indices"
    val TOTAL_INDICES = "total_indices"
    val TYPE = "type"
    val VALUES = "values"
    val VEC = "vec"
  }

}
