package com.klibisz.elastiknn.api

import com.klibisz.elastiknn.ELASTIKNN_NAME
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent._

import java.io.ByteArrayOutputStream
import scala.collection.mutable.ArrayBuffer

object XContentCodec {

  trait Encoder[T] {
    def encodeUnsafeInner(t: T, b: XContentBuilder): Unit
    def encodeUnsafe(t: T, b: XContentBuilder): Unit = {
      b.startObject()
      encodeUnsafeInner(t, b)
      b.endObject()
    }
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

  def decodeUnsafeFromList[T](l: java.util.List[Object])(implicit c: Decoder[T]): T = {
    val bos = new ByteArrayOutputStream()
    val builder = new XContentBuilder(xcJson, bos)
    builder.value(l)
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

    implicit val similarity: Encoder[Similarity] = new Encoder[Similarity] {
      override def encodeUnsafeInner(t: Similarity, b: XContentBuilder): Unit = {
        t match {
          case Similarity.Jaccard => b.value(Names.JACCARD)
          case Similarity.Hamming => b.value(Names.HAMMING)
          case Similarity.L1      => b.value(Names.L1)
          case Similarity.L2      => b.value(Names.L2)
          case Similarity.Cosine  => b.value(Names.COSINE)
        }
      }
      override def encodeUnsafe(t: Similarity, b: XContentBuilder): Unit = encodeUnsafeInner(t, b)
    }

    implicit val denseFloatVec: Encoder[Vec.DenseFloat] = new Encoder[Vec.DenseFloat] {
      override def encodeUnsafeInner(t: Vec.DenseFloat, b: XContentBuilder): Unit = {
        b.array(Names.VALUES, t.values)
      }
      override def encodeUnsafe(t: Vec.DenseFloat, b: XContentBuilder): Unit = {
        b.startObject()
        encodeUnsafeInner(t, b)
        b.endObject()
      }
    }

    implicit val sparseBoolVec: Encoder[Vec.SparseBool] = new Encoder[Vec.SparseBool] {
      override def encodeUnsafeInner(t: Vec.SparseBool, b: XContentBuilder): Unit = {
        b.field(Names.TOTAL_INDICES, t.totalIndices)
        b.array(Names.TRUE_INDICES, t.trueIndices)
      }
      override def encodeUnsafe(t: Vec.SparseBool, b: XContentBuilder): Unit = {
        b.startObject()
        encodeUnsafeInner(t, b)
        b.endObject()
      }
    }

    implicit val emptyVec: Encoder[Vec.Empty] = new Encoder[Vec.Empty] {
      override def encodeUnsafeInner(t: Vec.Empty, b: XContentBuilder): Unit = ()
    }

    implicit val indexedVec: Encoder[Vec.Indexed] = new Encoder[Vec.Indexed] {
      override def encodeUnsafeInner(t: Vec.Indexed, b: XContentBuilder): Unit = {
        b.field(Names.FIELD, t.field)
        b.field(Names.ID, t.id)
        b.field(Names.INDEX, t.index)
      }
    }

    implicit val vec: Encoder[Vec] = new Encoder[Vec] {
      override def encodeUnsafeInner(t: Vec, b: XContentBuilder): Unit =
        t match {
          case dfv: Vec.DenseFloat => denseFloatVec.encodeUnsafeInner(dfv, b)
          case sbv: Vec.SparseBool => sparseBoolVec.encodeUnsafeInner(sbv, b)
          case iv: Vec.Indexed     => indexedVec.encodeUnsafeInner(iv, b)
          case empty: Vec.Empty    => emptyVec.encodeUnsafeInner(empty, b)
        }
      override def encodeUnsafe(t: Vec, b: XContentBuilder): Unit = t match {
        case dfv: Vec.DenseFloat => denseFloatVec.encodeUnsafe(dfv, b)
        case sbv: Vec.SparseBool => sparseBoolVec.encodeUnsafe(sbv, b)
        case iv: Vec.Indexed     => indexedVec.encodeUnsafe(iv, b)
        case empty: Vec.Empty    => emptyVec.encodeUnsafe(empty, b)
      }
    }

    implicit val sparseBoolMapping: Encoder[Mapping.SparseBool] = new Encoder[Mapping.SparseBool] {
      override def encodeUnsafeInner(t: Mapping.SparseBool, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.DIMS, t.dims)
        b.field(Names.MODEL, Names.EXACT)
        b.endObject()
      }
      override def encodeUnsafe(t: Mapping.SparseBool, b: XContentBuilder): Unit = {
        b.startObject()
        encodeUnsafeInner(t, b)
        b.field(Names.TYPE, Names.EKNN_SPARSE_BOOL_VECTOR)
        b.endObject()
      }
    }

    implicit val jaccardLshMapping: Encoder[Mapping.JaccardLsh] = new Encoder[Mapping.JaccardLsh] {
      override def encodeUnsafeInner(t: Mapping.JaccardLsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.LSH_L, t.L)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY, Names.JACCARD)
        b.endObject()
      }
      override def encodeUnsafe(t: Mapping.JaccardLsh, b: XContentBuilder): Unit = {
        b.startObject()
        encodeUnsafeInner(t, b)
        b.field(Names.TYPE, Names.EKNN_SPARSE_BOOL_VECTOR)
        b.endObject()
      }
    }

    implicit val hammingLshMapping: Encoder[Mapping.HammingLsh] = new Encoder[Mapping.HammingLsh] {
      override def encodeUnsafeInner(t: Mapping.HammingLsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.LSH_L, t.L)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY, Names.HAMMING)
        b.endObject()
      }
      override def encodeUnsafe(t: Mapping.HammingLsh, b: XContentBuilder): Unit = {
        b.startObject()
        encodeUnsafeInner(t, b)
        b.field(Names.TYPE, Names.EKNN_SPARSE_BOOL_VECTOR)
        b.endObject()
      }
    }

    implicit val denseFloatMapping: Encoder[Mapping.DenseFloat] = new Encoder[Mapping.DenseFloat] {
      override def encodeUnsafeInner(t: Mapping.DenseFloat, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.DIMS, t.dims)
        b.field(Names.MODEL, Names.EXACT)
        b.endObject()
      }
      override def encodeUnsafe(t: Mapping.DenseFloat, b: XContentBuilder): Unit = {
        b.startObject()
        encodeUnsafeInner(t, b)
        b.field(Names.TYPE, Names.EKNN_DENSE_FLOAT_VECTOR)
        b.endObject()
      }
    }

    implicit val cosineLshMapping: Encoder[Mapping.CosineLsh] = new Encoder[Mapping.CosineLsh] {
      override def encodeUnsafeInner(t: Mapping.CosineLsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.LSH_L, t.L)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY, Names.COSINE)
        b.endObject()
      }
      override def encodeUnsafe(t: Mapping.CosineLsh, b: XContentBuilder): Unit = {
        b.startObject()
        encodeUnsafeInner(t, b)
        b.field(Names.TYPE, Names.EKNN_DENSE_FLOAT_VECTOR)
        b.endObject()
      }
    }

    implicit val l2LshMapping: Encoder[Mapping.L2Lsh] = new Encoder[Mapping.L2Lsh] {
      override def encodeUnsafeInner(t: Mapping.L2Lsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.LSH_L, t.L)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY, Names.L2)
        b.field(Names.LSH_W, t.w)
        b.endObject()
      }
      override def encodeUnsafe(t: Mapping.L2Lsh, b: XContentBuilder): Unit = {
        b.startObject()
        encodeUnsafeInner(t, b)
        b.field(Names.TYPE, Names.EKNN_DENSE_FLOAT_VECTOR)
        b.endObject()
      }
    }

    implicit val permutationLshMapping: Encoder[Mapping.PermutationLsh] = new Encoder[Mapping.PermutationLsh] {
      override def encodeUnsafeInner(t: Mapping.PermutationLsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.PERMUTATION_LSH)
        b.field(Names.REPEATING, t.repeating)
        b.endObject()
      }
      override def encodeUnsafe(t: Mapping.PermutationLsh, b: XContentBuilder): Unit = {
        b.startObject()
        encodeUnsafeInner(t, b)
        b.field(Names.TYPE, Names.EKNN_DENSE_FLOAT_VECTOR)
        b.endObject()
      }
    }

    implicit val mapping: Encoder[Mapping] = new Encoder[Mapping] {
      override def encodeUnsafeInner(t: Mapping, b: XContentBuilder): Unit =
        t match {
          case m: Mapping.SparseBool     => sparseBoolMapping.encodeUnsafeInner(m, b)
          case m: Mapping.JaccardLsh     => jaccardLshMapping.encodeUnsafeInner(m, b)
          case m: Mapping.HammingLsh     => hammingLshMapping.encodeUnsafeInner(m, b)
          case m: Mapping.DenseFloat     => denseFloatMapping.encodeUnsafeInner(m, b)
          case m: Mapping.CosineLsh      => cosineLshMapping.encodeUnsafeInner(m, b)
          case m: Mapping.L2Lsh          => l2LshMapping.encodeUnsafeInner(m, b)
          case m: Mapping.PermutationLsh => permutationLshMapping.encodeUnsafeInner(m, b)
        }
      override def encodeUnsafe(t: Mapping, b: XContentBuilder): Unit =
        t match {
          case m: Mapping.SparseBool     => sparseBoolMapping.encodeUnsafe(m, b)
          case m: Mapping.JaccardLsh     => jaccardLshMapping.encodeUnsafe(m, b)
          case m: Mapping.HammingLsh     => hammingLshMapping.encodeUnsafe(m, b)
          case m: Mapping.DenseFloat     => denseFloatMapping.encodeUnsafe(m, b)
          case m: Mapping.CosineLsh      => cosineLshMapping.encodeUnsafe(m, b)
          case m: Mapping.L2Lsh          => l2LshMapping.encodeUnsafe(m, b)
          case m: Mapping.PermutationLsh => permutationLshMapping.encodeUnsafe(m, b)
        }
    }

    implicit val exactQuery: Encoder[NearestNeighborsQuery.Exact] = new Encoder[NearestNeighborsQuery.Exact] {
      override def encodeUnsafeInner(t: NearestNeighborsQuery.Exact, b: XContentBuilder): Unit = {
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.EXACT)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
      }
    }

    implicit val jaccardLshQuery: Encoder[NearestNeighborsQuery.JaccardLsh] = new Encoder[NearestNeighborsQuery.JaccardLsh] {
      override def encodeUnsafeInner(t: NearestNeighborsQuery.JaccardLsh, b: XContentBuilder): Unit = {
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
      }
    }

    implicit val hammingLshQuery: Encoder[NearestNeighborsQuery.HammingLsh] = new Encoder[NearestNeighborsQuery.HammingLsh] {
      override def encodeUnsafeInner(t: NearestNeighborsQuery.HammingLsh, b: XContentBuilder): Unit = {
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
      }
    }

    implicit val cosineLshQuery: Encoder[NearestNeighborsQuery.CosineLsh] = new Encoder[NearestNeighborsQuery.CosineLsh] {
      override def encodeUnsafeInner(t: NearestNeighborsQuery.CosineLsh, b: XContentBuilder): Unit = {
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
      }
    }

    implicit val l2LshQuery: Encoder[NearestNeighborsQuery.L2Lsh] = new Encoder[NearestNeighborsQuery.L2Lsh] {
      override def encodeUnsafeInner(t: NearestNeighborsQuery.L2Lsh, b: XContentBuilder): Unit = {
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.PROBES, t.probes)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
      }
    }

    implicit val permutationLshQuery: Encoder[NearestNeighborsQuery.PermutationLsh] = new Encoder[NearestNeighborsQuery.PermutationLsh] {
      override def encodeUnsafeInner(t: NearestNeighborsQuery.PermutationLsh, b: XContentBuilder): Unit = {
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.PERMUTATION_LSH)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
      }
    }

    implicit val nearestNeighborsQuery: Encoder[NearestNeighborsQuery] = new Encoder[NearestNeighborsQuery] {
      override def encodeUnsafeInner(t: NearestNeighborsQuery, b: XContentBuilder): Unit =
        t match {
          case q: NearestNeighborsQuery.Exact          => exactQuery.encodeUnsafeInner(q, b)
          case q: NearestNeighborsQuery.JaccardLsh     => jaccardLshQuery.encodeUnsafeInner(q, b)
          case q: NearestNeighborsQuery.HammingLsh     => hammingLshQuery.encodeUnsafeInner(q, b)
          case q: NearestNeighborsQuery.CosineLsh      => cosineLshQuery.encodeUnsafeInner(q, b)
          case q: NearestNeighborsQuery.L2Lsh          => l2LshQuery.encodeUnsafeInner(q, b)
          case q: NearestNeighborsQuery.PermutationLsh => permutationLshQuery.encodeUnsafeInner(q, b)
        }
    }
  }

  object Decoder {

    private def unexpectedName(current: String): String =
      s"Unexpected name [$current]"

    private def unexpectedName(current: String, expected: String): String =
      s"Expected name [$expected] but found [${current}]"

    private def unexpectedValue(s: String): String =
      s"Unexpected value [$s]"

    private def unexpectedToken(current: Token, expected: Token*): String =
      s"Expected token to be one of [${expected.mkString(",")}] but found [${current}]"

    private def unableToConstruct(tipe: String): String =
      s"Unable to construct [$tipe] from parsed JSON"

    private def assertToken(current: Token, expected: Token*): Unit =
      if (expected.contains(current)) () else throw new XContentParseException(unexpectedToken(current, expected: _*))

//    private def assertName(current: String, expected: String): Unit =
//      if (current == expected) () else throw new XContentParseException(unexpectedName(current, expected))

    private def parseFloatArray(p: XContentParser, expectedLength: Int): Array[Float] = {
      assertToken(p.currentToken(), Token.START_ARRAY, Token.VALUE_NUMBER)
      val b = new ArrayBuffer[Float](expectedLength)
      while (p.nextToken() != Token.END_ARRAY) {
        assertToken(p.currentToken(), Token.VALUE_NUMBER)
        b.append(p.numberValue().floatValue())
      }
      b.toArray
    }

    private def parseSparseBoolArray(p: XContentParser, expectedLength: Int): Array[Int] = {
      assertToken(p.currentToken(), Token.START_ARRAY, Token.VALUE_NUMBER)
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

    implicit val vec: Decoder[Vec] = new Decoder[Vec] {
      override def decodeUnsafe(p: XContentParser): Vec = {
        var field: Option[String] = None
        var id: Option[String] = None
        var index: Option[String] = None
        var isEmpty: Boolean = true
        var trueIndices: Option[Array[Int]] = None
        var totalIndices: Option[Int] = None
        var values: Option[Array[Float]] = None
        if (p.currentToken() != Token.START_OBJECT && p.currentToken() != Token.START_ARRAY) {
          assertToken(p.nextToken(), Token.START_OBJECT, Token.START_ARRAY)
        }
        p.currentToken() match {
          case Token.START_OBJECT =>
            while (p.nextToken() == Token.FIELD_NAME) {
              isEmpty = false
              p.currentName() match {
                case Names.FIELD if p.nextToken() == Token.VALUE_STRING         => field = Some(p.text())
                case Names.ID if p.nextToken() == Token.VALUE_STRING            => id = Some(p.text())
                case Names.INDEX if p.nextToken() == Token.VALUE_STRING         => index = Some(p.text())
                case Names.TRUE_INDICES if p.nextToken() == Token.START_ARRAY   => trueIndices = Some(parseSparseBoolArray(p, 42))
                case Names.TOTAL_INDICES if p.nextToken() == Token.VALUE_NUMBER => totalIndices = Some(p.intValue())
                case Names.VALUES if p.nextToken() == Token.START_ARRAY         => values = Some(parseFloatArray(p, 42))
                case n                                                          => throw new XContentParseException(unexpectedName(n))
              }
            }
          case Token.START_ARRAY =>
            isEmpty = false
            p.nextToken() match {
              case Token.END_ARRAY =>
                values = Some(Array.empty)
              case Token.VALUE_NUMBER =>
                values = Some(p.floatValue() +: parseFloatArray(p, 42))
              case Token.START_ARRAY =>
                trueIndices = Some(parseSparseBoolArray(p, 42))
                assertToken(p.nextToken(), Token.VALUE_NUMBER)
                totalIndices = Some(p.intValue())
              case t =>
                throw new XContentParseException(unexpectedToken(t, Token.END_ARRAY, Token.VALUE_NUMBER, Token.START_ARRAY))
            }
          case _ =>
            throw new XContentParseException(unexpectedToken(p.currentToken(), Token.START_OBJECT, Token.START_ARRAY))
        }
        if (isEmpty) Vec.Empty()
        else
          (field, id, index, trueIndices, totalIndices, values) match {
            case (Some(field), Some(id), Some(index), _, _, _) =>
              Vec.Indexed(index = index, id = id, field = field)
            case (_, _, _, Some(trueIndices), Some(totalIndices), _) =>
              Vec.SparseBool(trueIndices, totalIndices)
            case (_, _, _, _, _, Some(values)) =>
              Vec.DenseFloat(values)
            case _ => throw new XContentParseException(unableToConstruct("vector"))
          }
      }
    }

    implicit val denseFloatVec: Decoder[Vec.DenseFloat] = (p: XContentParser) =>
      vec.decodeUnsafe(p) match {
        case v: Vec.DenseFloat => v
        case _                 => throw new XContentParseException(unableToConstruct("dense float vector"))
      }

    implicit val sparseBoolVec: Decoder[Vec.SparseBool] =
      (p: XContentParser) =>
        vec.decodeUnsafe(p) match {
          case v: Vec.SparseBool => v
          case _                 => throw new XContentParseException(unableToConstruct("sparse bool vector"))
        }

    implicit val emptyVec: Decoder[Vec.Empty] = (p: XContentParser) =>
      vec.decodeUnsafe(p) match {
        case v: Vec.Empty => v
        case _            => throw new XContentParseException(unableToConstruct("empty vector"))
      }

    implicit val indexedVec: Decoder[Vec.Indexed] = (p: XContentParser) =>
      vec.decodeUnsafe(p) match {
        case v: Vec.Indexed => v
        case _              => throw new XContentParseException(unableToConstruct("indexed vector"))
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
      if (p.currentToken() != Token.START_OBJECT) {
        assertToken(p.nextToken(), Token.START_OBJECT)
      }
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
                case n                                                       => throw new XContentParseException(unexpectedName(n))
              }
            }
          case (n, _) => throw new XContentParseException(unexpectedName(n))
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
        case _ => throw new XContentParseException(unableToConstruct("mapping"))
      }
    }

    implicit val nearestNeighborsQuery: Decoder[NearestNeighborsQuery] =
      (p: XContentParser) => {
        var candidates: Option[Int] = None
        var field: Option[String] = None
        var model: Option[String] = None
        var probes: Option[Int] = None
        var similarity: Option[Similarity] = None
        var vec: Option[Vec] = None
        if (p.currentToken() != Token.START_OBJECT) {
          assertToken(p.nextToken(), Token.START_OBJECT)
        }
        while (p.nextToken() == Token.FIELD_NAME) {
          p.currentName() match {
            case Names.CANDIDATES if p.nextToken() == Token.VALUE_NUMBER => candidates = Some(p.intValue())
            case Names.FIELD if p.nextToken() == Token.VALUE_STRING      => field = Some(p.text())
            case Names.MODEL if p.nextToken() == Token.VALUE_STRING      => model = Some(p.text())
            case Names.PROBES if p.nextToken() == Token.VALUE_NUMBER     => probes = Some(p.intValue())
            case Names.SIMILARITY                                        => similarity = Some(decodeUnsafe[Similarity](p))
            case Names.VEC                                               => vec = Some(decodeUnsafe[Vec](p))
            case n                                                       => throw new XContentParseException(unexpectedName(n))
          }
        }
        (candidates, field, model, probes, similarity, vec) match {
          case (_, Some(field), Some(Names.EXACT), _, Some(similarity), Some(v)) =>
            NearestNeighborsQuery.Exact(field, similarity, v)
          case (Some(candidates), Some(field), Some(Names.LSH), _, Some(Similarity.Cosine), Some(v)) =>
            NearestNeighborsQuery.CosineLsh(field, candidates, v)
          case (Some(candidates), Some(field), Some(Names.LSH), _, Some(Similarity.Hamming), Some(v)) =>
            NearestNeighborsQuery.HammingLsh(field, candidates, v)
          case (Some(candidates), Some(field), Some(Names.LSH), _, Some(Similarity.Jaccard), Some(v)) =>
            NearestNeighborsQuery.JaccardLsh(field, candidates, v)
          case (Some(candidates), Some(field), Some(Names.LSH), Some(probes), Some(Similarity.L2), Some(v)) =>
            NearestNeighborsQuery.L2Lsh(field, candidates, probes, v)
          case (Some(candidates), Some(field), Some(Names.PERMUTATION_LSH), _, Some(similarity), Some(v)) =>
            NearestNeighborsQuery.PermutationLsh(field, similarity, candidates, v)
          case _ => throw new XContentParseException(unableToConstruct("nearest neighbors query"))
        }
      }
  }

  private object Names {
    val ANGULAR = "angular"
    val CANDIDATES = "candidates"
    val COSINE = "cosine"
    val DIMS = "dims"
    val ELASTIKNN = "elastiknn"
    val EKNN_DENSE_FLOAT_VECTOR = s"${ELASTIKNN_NAME}_dense_float_vector"
    val EKNN_SPARSE_BOOL_VECTOR = s"${ELASTIKNN_NAME}_sparse_bool_vector"
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
    val PROBES = "probes"
    val REPEATING = "repeating"
    val SIMILARITY = "similarity"
    val TRUE_INDICES = "true_indices"
    val TOTAL_INDICES = "total_indices"
    val TYPE = "type"
    val VALUES = "values"
    val VEC = "vec"
  }
}
