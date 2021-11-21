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
    val builder = new XContentBuilder(xcJson, bos)
    builder.map(m)
    builder.close()
    val parser = xcJson.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, bos.toByteArray)
    c.decodeUnsafe(parser)
  }

  def decodeUnsafeFromString[T](str: String)(implicit d: Decoder[T]): T =
    decodeUnsafeFromByteArray(str.getBytes)

  def decodeUnsafeFromByteArray[T](barr: Array[Byte])(implicit c: Decoder[T]): T = {
    val p = xcJson.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, barr)
    c.decodeUnsafe(p)
  }

  object Encoder {

    implicit val similarity: Encoder[Similarity] = (t: Similarity, b: XContentBuilder) =>
      t match {
        case Similarity.Jaccard => b.value(Names.JACCARD)
        case Similarity.Hamming => b.value(Names.HAMMING)
        case Similarity.L1      => b.value(Names.L1)
        case Similarity.L2      => b.value(Names.L2)
        case Similarity.Cosine  => b.value(Names.COSINE)
      }

    implicit val denseFloatVec: Encoder[Vec.DenseFloat] = (t: Vec.DenseFloat, b: XContentBuilder) => {
      b.startObject()
      b.array(Names.VALUES, t.values)
      b.endObject()
    }

    implicit val sparseBoolVec: Encoder[Vec.SparseBool] = (t: Vec.SparseBool, b: XContentBuilder) => {
      b.startObject()
      b.field(Names.TOTAL_INDICES, t.totalIndices)
      b.array(Names.TRUE_INDICES, t.trueIndices)
      b.endObject()
    }

    implicit val emptyVec: Encoder[Vec.Empty] = (t: Vec.Empty, x: XContentBuilder) => {
      x.startObject()
      x.endObject()
    }

    implicit val indexedVec: Encoder[Vec.Indexed] = (t: Vec.Indexed, x: XContentBuilder) => {
      x.startObject()
      x.field(Names.FIELD, t.field)
      x.field(Names.ID, t.id)
      x.field(Names.INDEX, t.index)
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
      x.startObject(Names.ELASTIKNN)
      x.field(Names.DIMS, m.dims)
      x.field(Names.MODEL, Names.EXACT)
      x.endObject()
      x.field(Names.TYPE, Names.EKNN_SPARSE_BOOL_VECTOR)
      x.endObject()
    }

    implicit val jaccardLshMapping: Encoder[Mapping.JaccardLsh] = (m: Mapping.JaccardLsh, x: XContentBuilder) => {
      x.startObject()
      x.startObject(Names.ELASTIKNN)
      x.field(Names.LSH_L, m.L)
      x.field(Names.DIMS, m.dims)
      x.field(Names.LSH_K, m.k)
      x.field(Names.MODEL, Names.LSH)
      x.field(Names.SIMILARITY, Names.JACCARD)
      x.endObject()
      x.field(Names.TYPE, Names.EKNN_SPARSE_BOOL_VECTOR)
      x.endObject()
    }

    implicit val hammingLshMapping: Encoder[Mapping.HammingLsh] = (m: Mapping.HammingLsh, x: XContentBuilder) => {
      x.startObject()
      x.startObject(Names.ELASTIKNN)
      x.field(Names.LSH_L, m.L)
      x.field(Names.DIMS, m.dims)
      x.field(Names.LSH_K, m.k)
      x.field(Names.MODEL, Names.LSH)
      x.field(Names.SIMILARITY, Names.HAMMING)
      x.endObject()
      x.field(Names.TYPE, Names.EKNN_SPARSE_BOOL_VECTOR)
      x.endObject()
    }

    implicit val denseFloatMapping: Encoder[Mapping.DenseFloat] = (m: Mapping.DenseFloat, x: XContentBuilder) => {
      x.startObject()
      x.startObject(Names.ELASTIKNN)
      x.field(Names.DIMS, m.dims)
      x.field(Names.MODEL, Names.EXACT)
      x.endObject()
      x.field(Names.TYPE, Names.EKNN_DENSE_FLOAT_VECTOR)
      x.endObject()
    }

    implicit val cosineLshMapping: Encoder[Mapping.CosineLsh] = (m: Mapping.CosineLsh, x: XContentBuilder) => {
      x.startObject()
      x.startObject(Names.ELASTIKNN)
      x.field(Names.LSH_L, m.L)
      x.field(Names.DIMS, m.dims)
      x.field(Names.LSH_K, m.k)
      x.field(Names.MODEL, Names.LSH)
      x.field(Names.SIMILARITY, Names.COSINE)
      x.endObject()
      x.field(Names.TYPE, Names.EKNN_DENSE_FLOAT_VECTOR)
      x.endObject()
    }

    implicit val l2LshMapping: Encoder[Mapping.L2Lsh] = (m: Mapping.L2Lsh, x: XContentBuilder) => {
      x.startObject()
      x.startObject(Names.ELASTIKNN)
      x.field(Names.LSH_L, m.L)
      x.field(Names.DIMS, m.dims)
      x.field(Names.LSH_K, m.k)
      x.field(Names.MODEL, Names.LSH)
      x.field(Names.SIMILARITY, Names.L2)
      x.field(Names.LSH_W, m.w)
      x.endObject()
      x.field(Names.TYPE, Names.EKNN_DENSE_FLOAT_VECTOR)
      x.endObject()
    }

    implicit val permutationLshMapping: Encoder[Mapping.PermutationLsh] = (m: Mapping.PermutationLsh, x: XContentBuilder) => {
      x.startObject()
      x.startObject(Names.ELASTIKNN)
      x.field(Names.DIMS, m.dims)
      x.field(Names.LSH_K, m.k)
      x.field(Names.MODEL, Names.PERMUTATION_LSH)
      x.field(Names.REPEATING, m.repeating)
      x.endObject()
      x.field(Names.TYPE, Names.EKNN_DENSE_FLOAT_VECTOR)
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

    implicit val exactQuery: Encoder[NearestNeighborsQuery.Exact] = (t: NearestNeighborsQuery.Exact, x: XContentBuilder) => {
      x.startObject()
      x.field(Names.FIELD, t.field)
      x.field(Names.SIMILARITY)
      similarity.encodeUnsafe(t.similarity, x)
      x.field(Names.VEC)
      vec.encodeUnsafe(t.vec, x)
      x.endObject()
    }

    implicit val nearestNeighborsQuery: Encoder[NearestNeighborsQuery] =
      (t: NearestNeighborsQuery, b: XContentBuilder) =>
        t match {
          case q: NearestNeighborsQuery.Exact                                           => exactQuery.encodeUnsafe(q, b)
          case NearestNeighborsQuery.JaccardLsh(field, candidates, vec)                 => ???
          case NearestNeighborsQuery.HammingLsh(field, candidates, vec)                 => ???
          case NearestNeighborsQuery.CosineLsh(field, candidates, vec)                  => ???
          case NearestNeighborsQuery.L2Lsh(field, candidates, probes, vec)              => ???
          case NearestNeighborsQuery.PermutationLsh(field, similarity, candidates, vec) => ???
        }
  }

  object Decoder {

    private def missingName(name: String): String =
      s"Expected to find name [$name] but did not"

    private def missingNames(names: String*): String =
      s"Expected to find name [${names.mkString(",")}] but did some or all were missing"

    private def unexpectedName(current: String, expected: String): String =
      s"Expected name [$expected] but found [${current}]"

    private def unexpectedNameAndToken(currentName: String, currentToken: Token): String =
      s"Unexpected name [$currentName] and token [$currentToken]"

    private def unexpectedValue(s: String): String =
      s"Unexpected value [$s]"

    private def unexpectedToken(current: Token, expected: Token*): String =
      s"Expected token to be one of [${expected.mkString(",")}] but found [${current}]"

    private def assertToken(current: XContentParser.Token, expected: XContentParser.Token): Unit =
      if (current == expected) () else throw new XContentParseException(unexpectedToken(current, expected))

    private def assertName(current: String, expected: String): Unit =
      if (current == expected) () else throw new XContentParseException(unexpectedName(current, expected))

    private def decodeFloatArray(p: XContentParser, expectedLength: Int): Array[Float] = {
      assertToken(p.currentToken(), Token.START_ARRAY)
      val b = new ArrayBuffer[Float](expectedLength)
      while (p.nextToken() != Token.END_ARRAY) {
        assertToken(p.currentToken(), Token.VALUE_NUMBER)
        b.append(p.numberValue().floatValue())
      }
      b.toArray
    }

    private def parseSparseBoolArray(p: XContentParser, expectedLength: Int): Array[Int] = {
      assertToken(p.currentToken(), Token.START_ARRAY)
      val b = new ArrayBuffer[Int](expectedLength)
      while (p.nextToken() != Token.END_ARRAY) {
        assertToken(p.currentToken(), Token.VALUE_NUMBER)
        b.append(p.numberValue().intValue())
      }
      b.toArray
    }

    implicit val similarity: Decoder[Similarity] = (p: XContentParser) => {
      assertToken(p.nextToken(), Token.VALUE_STRING)
      val s1 = p.text()
      val s2 = s1.toLowerCase
      s2 match {
        case Names.JACCARD => Similarity.Jaccard
        case Names.HAMMING => Similarity.Hamming
        case Names.L1      => Similarity.L1
        case Names.L2      => Similarity.L2
        case Names.COSINE  => Similarity.Cosine
        case Names.ANGULAR => Similarity.Cosine
        case _             => throw new XContentParseException(unexpectedValue(s1))
      }
    }

    implicit val denseFloatVec: Decoder[Vec.DenseFloat] = new Decoder[Vec.DenseFloat] {
      @tailrec
      override def decodeUnsafe(p: XContentParser): Vec.DenseFloat =
        p.nextToken() match {
          case Token.START_ARRAY =>
            Vec.DenseFloat(decodeFloatArray(p, 42))
          case Token.START_OBJECT if p.nextToken() == Token.FIELD_NAME =>
            assertName(p.currentName(), Names.VALUES)
            decodeUnsafe(p) // Recursive call to parse as an array.
          case t => throw new XContentParseException(unexpectedToken(t, Token.START_OBJECT, Token.START_ARRAY))
        }
    }

    implicit val sparseBoolVec: Decoder[Vec.SparseBool] = (p: XContentParser) => {
      p.nextToken() match {
        case Token.START_ARRAY => // Shorthand format: [totalIndices, [ix1, ix2, ...]]
          assertToken(p.nextToken(), Token.VALUE_NUMBER)
          val totalIndices = p.intValue()
          assertToken(p.nextToken(), Token.START_ARRAY)
          val trueIndices = parseSparseBoolArray(p, 42)
          Vec.SparseBool(trueIndices, totalIndices)
        case Token.START_OBJECT =>
          var trueIndices: Option[Array[Int]] = None
          var totalIndices: Option[Int] = None
          while (p.nextToken() == Token.FIELD_NAME) {
            (p.currentName(), p.nextToken()) match {
              case (Names.TRUE_INDICES, Token.START_ARRAY) =>
                trueIndices = Some(parseSparseBoolArray(p, 42))
              case (Names.TOTAL_INDICES, Token.VALUE_NUMBER) =>
                totalIndices = Some(p.intValue())
              case (n, t) => throw new XContentParseException(unexpectedNameAndToken(n, t))
            }
          }
          (trueIndices, totalIndices) match {
            case (Some(arr), Some(i)) => Vec.SparseBool(arr, i)
            case _                    => throw new XContentParseException(missingNames(Names.TRUE_INDICES, Names.TOTAL_INDICES))
          }
        case _ => throw new XContentParseException(unexpectedToken(p.currentToken(), Token.START_ARRAY, Token.START_OBJECT))
      }
    }

    implicit val emptyVec: Decoder[Vec.Empty] = (p: XContentParser) => {
      assertToken(p.nextToken(), Token.START_OBJECT)
      assertToken(p.nextToken(), Token.END_OBJECT)
      Vec.Empty()
    }

    implicit val indexedVec: Decoder[Vec.Indexed] = (p: XContentParser) => {
      var index: Option[String] = None
      var id: Option[String] = None
      var field: Option[String] = None
      assertToken(p.nextToken(), Token.START_OBJECT)
      while (p.nextToken() == Token.FIELD_NAME) {
        (p.currentName(), p.nextToken()) match {
          case (Names.INDEX, Token.VALUE_STRING) => index = Some(p.text())
          case (Names.ID, Token.VALUE_STRING)    => id = Some(p.text())
          case (Names.FIELD, Token.VALUE_STRING) => field = Some(p.text())
          case (n, t)                            => throw new XContentParseException(unexpectedNameAndToken(n, t))
        }
      }
      (index, id, field) match {
        case (Some(index_), Some(id_), Some(field_)) => Vec.Indexed(index_, id_, field_)
        case _                                       => throw new XContentParseException(missingNames(Names.INDEX, Names.ID, Names.FIELD))
      }
    }

    implicit val mapping: Decoder[Mapping] = (p: XContentParser) => {
      var typ: Option[String] = None
      var dims: Option[Int] = None
      var model: Option[String] = None
      var similarity: Option[Similarity] = None
      var l: Option[Int] = None
      var k: Option[Int] = None
      var w: Option[Int] = None
      var repeating: Option[Boolean] = None
      assertToken(p.nextToken(), Token.START_OBJECT)
      while (p.nextToken() == Token.FIELD_NAME) {
        (p.currentName(), p.nextToken()) match {
          case (Names.TYPE, Token.VALUE_STRING) => typ = Some(p.text())
          case (Names.ELASTIKNN, Token.START_OBJECT) =>
            while (p.nextToken() == Token.FIELD_NAME) {
              p.currentName() match {
                case Names.DIMS if p.nextToken() == Token.VALUE_NUMBER       => dims = Some(p.intValue())
                case Names.LSH_L if p.nextToken() == Token.VALUE_NUMBER      => l = Some(p.intValue())
                case Names.LSH_K if p.nextToken() == Token.VALUE_NUMBER      => k = Some(p.intValue())
                case Names.LSH_W if p.nextToken() == Token.VALUE_NUMBER      => w = Some(p.intValue())
                case Names.MODEL if p.nextToken() == Token.VALUE_STRING      => model = Some(p.text())
                case Names.REPEATING if p.nextToken() == Token.VALUE_BOOLEAN => repeating = Some(p.booleanValue())
                case Names.SIMILARITY                                        => similarity = Some(Decoder.similarity.decodeUnsafe(p))
                case n                                                       => throw new XContentParseException(unexpectedNameAndToken(n, p.nextToken()))
              }
            }
          case (n, t) => throw new XContentParseException(unexpectedNameAndToken(n, t))
        }
      }
      (typ, model, dims, similarity, l, k, w, repeating) match {
        case (Some(Names.EKNN_DENSE_FLOAT_VECTOR), Some(Names.EXACT) | None, Some(dims), _, _, _, _, _) =>
          Mapping.DenseFloat(dims)
        case (Some(Names.EKNN_SPARSE_BOOL_VECTOR), Some(Names.EXACT) | None, Some(dims), _, _, _, _, _) =>
          Mapping.SparseBool(dims)
        case (Some(Names.EKNN_SPARSE_BOOL_VECTOR), Some(Names.LSH), Some(dims), Some(Similarity.Jaccard), Some(l), Some(k), _, _) =>
          Mapping.JaccardLsh(dims, l, k)
        case (Some(Names.EKNN_SPARSE_BOOL_VECTOR), Some(Names.LSH), Some(dims), Some(Similarity.Hamming), Some(l), Some(k), _, _) =>
          Mapping.HammingLsh(dims, l, k)
        case (Some(Names.EKNN_DENSE_FLOAT_VECTOR), Some(Names.LSH), Some(dims), Some(Similarity.L2), Some(l), Some(k), Some(w), _) =>
          Mapping.L2Lsh(dims, l, k, w)
        case (Some(Names.EKNN_DENSE_FLOAT_VECTOR), Some(Names.LSH), Some(dims), Some(Similarity.Cosine), Some(l), Some(k), _, _) =>
          Mapping.CosineLsh(dims, l, k)
        case (Some(Names.EKNN_DENSE_FLOAT_VECTOR), Some(Names.PERMUTATION_LSH), Some(dims), _, _, Some(k), _, Some(repeating)) =>
          Mapping.PermutationLsh(dims, k, repeating)
        case _ => throw new XContentParseException(s"Unable to construct mapping from parsed JSON")
      }
    }

    implicit val nearestNeighborsQuery: Decoder[NearestNeighborsQuery] =
      (p: XContentParser) => {
        ???
      }

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

  private object Names {
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
