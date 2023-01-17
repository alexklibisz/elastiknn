package com.klibisz.elastiknn.api

import com.klibisz.elastiknn.ELASTIKNN_NAME
import org.elasticsearch.xcontent.XContentParser.Token
import org.elasticsearch.xcontent.XContentParser.Token._
import org.elasticsearch.xcontent._

import java.io.ByteArrayOutputStream
import scala.collection.immutable.SortedSet
import scala.collection.mutable.ArrayBuffer

/**
  * JSON codec for Elastiknn API types, implemented using the Elasticsearch XContentBuilder and XContentParser.
  */
object XContentCodec {

  trait Encoder[T] {
    def encodeUnsafe(t: T, b: XContentBuilder): Unit
  }

  trait Decoder[T] {
    def decodeUnsafe(p: XContentParser): T
  }

  // Special encoder type just for mappings.
  // Splits up the encoding to handle some messy details within the plugin.
  sealed trait MappingEncoder[T <: Mapping] extends Encoder[T] {
    protected def vectorType: String
    private[elastiknn] def encodeElastiknnObject(t: T, b: XContentBuilder): Unit
    override def encodeUnsafe(t: T, b: XContentBuilder): Unit = {
      b.startObject()
      encodeElastiknnObject(t, b)
      b.field(Names.TYPE, vectorType)
      b.endObject()
    }
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
      override def encodeUnsafe(t: Similarity, b: XContentBuilder): Unit = {
        t match {
          case Similarity.Jaccard => b.value(Names.JACCARD)
          case Similarity.Hamming => b.value(Names.HAMMING)
          case Similarity.L1      => b.value(Names.L1)
          case Similarity.L2      => b.value(Names.L2)
          case Similarity.Cosine  => b.value(Names.COSINE)
        }
      }
    }

    implicit val denseFloatVec: Encoder[Vec.DenseFloat] = new Encoder[Vec.DenseFloat] {
      override def encodeUnsafe(t: Vec.DenseFloat, b: XContentBuilder): Unit = {
        b.startObject()
        b.array(Names.VALUES, t.values)
        b.endObject()
      }
    }

    implicit val sparseBoolVec: Encoder[Vec.SparseBool] = new Encoder[Vec.SparseBool] {
      override def encodeUnsafe(t: Vec.SparseBool, b: XContentBuilder): Unit = {
        b.startObject()
        b.field(Names.TOTAL_INDICES, t.totalIndices)
        b.array(Names.TRUE_INDICES, t.trueIndices)
        b.endObject()
      }
    }

    implicit val emptyVec: Encoder[Vec.Empty] = new Encoder[Vec.Empty] {
      override def encodeUnsafe(t: Vec.Empty, b: XContentBuilder): Unit = {
        b.startObject()
        b.endObject()
      }
    }

    implicit val indexedVec: Encoder[Vec.Indexed] = new Encoder[Vec.Indexed] {
      override def encodeUnsafe(t: Vec.Indexed, b: XContentBuilder): Unit = {
        b.startObject()
        b.field(Names.FIELD, t.field)
        b.field(Names.ID, t.id)
        b.field(Names.INDEX, t.index)
        b.endObject()
      }
    }

    implicit val vec: Encoder[Vec] = new Encoder[Vec] {
      override def encodeUnsafe(t: Vec, b: XContentBuilder): Unit =
        t match {
          case dfv: Vec.DenseFloat => denseFloatVec.encodeUnsafe(dfv, b)
          case sbv: Vec.SparseBool => sparseBoolVec.encodeUnsafe(sbv, b)
          case iv: Vec.Indexed     => indexedVec.encodeUnsafe(iv, b)
          case empty: Vec.Empty    => emptyVec.encodeUnsafe(empty, b)
        }
    }

    implicit val sparseBoolMapping: MappingEncoder[Mapping.SparseBool] = new MappingEncoder[Mapping.SparseBool] {
      override protected def vectorType: String = Names.EKNN_SPARSE_BOOL_VECTOR
      override def encodeElastiknnObject(t: Mapping.SparseBool, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.DIMS, t.dims)
        b.field(Names.MODEL, Names.EXACT)
        b.endObject()
      }
    }

    implicit val jaccardLshMapping: MappingEncoder[Mapping.JaccardLsh] = new MappingEncoder[Mapping.JaccardLsh] {
      override protected def vectorType: String = Names.EKNN_SPARSE_BOOL_VECTOR
      override def encodeElastiknnObject(t: Mapping.JaccardLsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.LSH_L, t.L)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY, Names.JACCARD)
        b.endObject()
      }
    }

    implicit val hammingLshMapping: MappingEncoder[Mapping.HammingLsh] = new MappingEncoder[Mapping.HammingLsh] {
      override protected def vectorType: String = Names.EKNN_SPARSE_BOOL_VECTOR
      override def encodeElastiknnObject(t: Mapping.HammingLsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.LSH_L, t.L)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY, Names.HAMMING)
        b.endObject()
      }
    }

    implicit val denseFloatMapping: MappingEncoder[Mapping.DenseFloat] = new MappingEncoder[Mapping.DenseFloat] {
      override protected def vectorType: String = Names.EKNN_DENSE_FLOAT_VECTOR
      override def encodeElastiknnObject(t: Mapping.DenseFloat, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.DIMS, t.dims)
        b.field(Names.MODEL, Names.EXACT)
        b.endObject()
      }
    }

    implicit val cosineLshMapping: MappingEncoder[Mapping.CosineLsh] = new MappingEncoder[Mapping.CosineLsh] {
      override protected def vectorType: String = Names.EKNN_DENSE_FLOAT_VECTOR
      override def encodeElastiknnObject(t: Mapping.CosineLsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.LSH_L, t.L)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY, Names.COSINE)
        b.endObject()
      }
    }

    implicit val l2LshMapping: MappingEncoder[Mapping.L2Lsh] = new MappingEncoder[Mapping.L2Lsh] {
      override protected def vectorType: String = Names.EKNN_DENSE_FLOAT_VECTOR
      override def encodeElastiknnObject(t: Mapping.L2Lsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.LSH_L, t.L)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY, Names.L2)
        b.field(Names.LSH_W, t.w)
        b.endObject()
      }
    }

    implicit val permutationLshMapping: MappingEncoder[Mapping.PermutationLsh] = new MappingEncoder[Mapping.PermutationLsh] {
      override protected def vectorType: String = Names.EKNN_DENSE_FLOAT_VECTOR
      override def encodeElastiknnObject(t: Mapping.PermutationLsh, b: XContentBuilder): Unit = {
        b.startObject(Names.ELASTIKNN)
        b.field(Names.DIMS, t.dims)
        b.field(Names.LSH_K, t.k)
        b.field(Names.MODEL, Names.PERMUTATION_LSH)
        b.field(Names.REPEATING, t.repeating)
        b.endObject()
      }
    }

    implicit val mapping: MappingEncoder[Mapping] = new MappingEncoder[Mapping] {
      override protected def vectorType: String = None.orNull
      override def encodeElastiknnObject(t: Mapping, b: XContentBuilder): Unit = t match {
        case m: Mapping.SparseBool     => sparseBoolMapping.encodeElastiknnObject(m, b)
        case m: Mapping.JaccardLsh     => jaccardLshMapping.encodeElastiknnObject(m, b)
        case m: Mapping.HammingLsh     => hammingLshMapping.encodeElastiknnObject(m, b)
        case m: Mapping.DenseFloat     => denseFloatMapping.encodeElastiknnObject(m, b)
        case m: Mapping.CosineLsh      => cosineLshMapping.encodeElastiknnObject(m, b)
        case m: Mapping.L2Lsh          => l2LshMapping.encodeElastiknnObject(m, b)
        case m: Mapping.PermutationLsh => permutationLshMapping.encodeElastiknnObject(m, b)
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
      override def encodeUnsafe(t: NearestNeighborsQuery.Exact, b: XContentBuilder): Unit = {
        b.startObject()
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.EXACT)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
        b.endObject()
      }
    }

    implicit val jaccardLshQuery: Encoder[NearestNeighborsQuery.JaccardLsh] = new Encoder[NearestNeighborsQuery.JaccardLsh] {
      override def encodeUnsafe(t: NearestNeighborsQuery.JaccardLsh, b: XContentBuilder): Unit = {
        b.startObject()
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
        b.endObject()
      }
    }

    implicit val hammingLshQuery: Encoder[NearestNeighborsQuery.HammingLsh] = new Encoder[NearestNeighborsQuery.HammingLsh] {
      override def encodeUnsafe(t: NearestNeighborsQuery.HammingLsh, b: XContentBuilder): Unit = {
        b.startObject()
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
        b.endObject()
      }
    }

    implicit val cosineLshQuery: Encoder[NearestNeighborsQuery.CosineLsh] = new Encoder[NearestNeighborsQuery.CosineLsh] {
      override def encodeUnsafe(t: NearestNeighborsQuery.CosineLsh, b: XContentBuilder): Unit = {
        b.startObject()
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
        b.endObject()
      }
    }

    implicit val l2LshQuery: Encoder[NearestNeighborsQuery.L2Lsh] = new Encoder[NearestNeighborsQuery.L2Lsh] {
      override def encodeUnsafe(t: NearestNeighborsQuery.L2Lsh, b: XContentBuilder): Unit = {
        b.startObject()
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.LSH)
        b.field(Names.PROBES, t.probes)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
        b.endObject()
      }
    }

    implicit val permutationLshQuery: Encoder[NearestNeighborsQuery.PermutationLsh] = new Encoder[NearestNeighborsQuery.PermutationLsh] {
      override def encodeUnsafe(t: NearestNeighborsQuery.PermutationLsh, b: XContentBuilder): Unit = {
        b.startObject()
        b.field(Names.CANDIDATES, t.candidates)
        b.field(Names.FIELD, t.field)
        b.field(Names.MODEL, Names.PERMUTATION_LSH)
        b.field(Names.SIMILARITY)
        similarity.encodeUnsafe(t.similarity, b)
        b.field(Names.VEC)
        vec.encodeUnsafe(t.vec, b)
        b.endObject()
      }
    }

    implicit val nearestNeighborsQuery: Encoder[NearestNeighborsQuery] = new Encoder[NearestNeighborsQuery] {
      override def encodeUnsafe(t: NearestNeighborsQuery, b: XContentBuilder): Unit =
        t match {
          case q: NearestNeighborsQuery.Exact          => exactQuery.encodeUnsafe(q, b)
          case q: NearestNeighborsQuery.JaccardLsh     => jaccardLshQuery.encodeUnsafe(q, b)
          case q: NearestNeighborsQuery.HammingLsh     => hammingLshQuery.encodeUnsafe(q, b)
          case q: NearestNeighborsQuery.CosineLsh      => cosineLshQuery.encodeUnsafe(q, b)
          case q: NearestNeighborsQuery.L2Lsh          => l2LshQuery.encodeUnsafe(q, b)
          case q: NearestNeighborsQuery.PermutationLsh => permutationLshQuery.encodeUnsafe(q, b)
        }
    }
  }

  object Decoder {

    private implicit val orderTokens: Ordering[Token] = new Ordering[Token] {
      override def compare(x: Token, y: Token): Int = x.name().compareTo(y.name())
    }

    private def unexpectedValue(text: String, expected: SortedSet[String]): String =
      s"Expected token to be one of [${expected.mkString(",")}] but found [$text]"

    private def unexpectedValue(name: String, text: String, expected: SortedSet[String]): String =
      s"Expected [$name] to be one of [${expected.mkString(",")}] but found [$text]"

    private def unexpectedToken(name: String, token: Token, expected: SortedSet[Token]): String =
      s"Expected [$name] to be one of [${expected.mkString(",")}] but found [$token]"

    private def unexpectedToken(token: Token, expected: SortedSet[Token]): String =
      s"Expected token to be one of [${expected.mkString(",")}] but found [$token]"

    private def unableToConstruct(typ: String): String =
      s"Unable to construct [$typ] from parsed JSON"

    private def assertToken(name: String, token: Token, expected: SortedSet[Token]): Unit =
      if (expected.contains(token)) () else throw new XContentParseException(unexpectedToken(name, token, expected))

    private def assertToken(token: Token, expected: SortedSet[Token]): Unit =
      if (expected.contains(token)) () else throw new XContentParseException(unexpectedToken(token, expected))

    private def assertToken(name: String, token: Token, expected: Token): Unit =
      assertToken(name, token, SortedSet(expected))

    private def assertToken(token: Token, expected: Token): Unit =
      assertToken(token, SortedSet(expected))

    private def assertValue(name: String, text: String, expected: SortedSet[String]): Unit =
      if (expected.contains(text)) () else throw new XContentParseException(unexpectedValue(name, text, expected))

    private def parseFloatArray(p: XContentParser, expectedLength: Int): Array[Float] = {
      val b = new ArrayBuffer[Float](expectedLength)
      p.currentToken() match {
        case START_ARRAY  => ()
        case VALUE_NUMBER => b.append(p.floatValue())
        case t            => throw new XContentParseException(unexpectedToken(t, SortedSet(START_ARRAY, VALUE_NUMBER)))
      }
      while (p.nextToken() != END_ARRAY) {
        assertToken(p.currentToken(), VALUE_NUMBER)
        b.append(p.floatValue())
      }
      b.toArray
    }

    private def parseSparseBoolArray(p: XContentParser, expectedLength: Int): Array[Int] = {
      val b = new ArrayBuffer[Int](expectedLength)
      p.currentToken() match {
        case START_ARRAY  => ()
        case VALUE_NUMBER => b.append(p.intValue())
        case t            => throw new XContentParseException(unexpectedToken(t, SortedSet(START_ARRAY, VALUE_NUMBER)))
      }
      while (p.nextToken() != END_ARRAY) {
        assertToken(p.currentToken(), VALUE_NUMBER)
        b.append(p.intValue())
      }
      b.toArray
    }

    implicit val similarity: Decoder[Similarity] = (p: XContentParser) => {
      if (p.currentToken() != VALUE_STRING) assertToken(p.nextToken(), VALUE_STRING)
      val s1 = p.text()
      val s2 = s1.toLowerCase
      s2 match {
        case Names.JACCARD => Similarity.Jaccard
        case Names.HAMMING => Similarity.Hamming
        case Names.L1      => Similarity.L1
        case Names.L2      => Similarity.L2
        case Names.COSINE  => Similarity.Cosine
        case Names.ANGULAR => Similarity.Cosine
        case _             => throw new XContentParseException(unexpectedValue(s1, Names.SIMILARITIES))
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
        if (p.currentToken() != START_OBJECT && p.currentToken() != START_ARRAY) {
          assertToken(p.nextToken(), SortedSet(START_ARRAY, START_OBJECT))
        }
        p.currentToken() match {
          case START_OBJECT =>
            while (p.nextToken() == FIELD_NAME) {
              isEmpty = false
              p.currentName() match {
                case n @ Names.FIELD =>
                  assertToken(n, p.nextToken(), VALUE_STRING)
                  field = Some(p.text())
                case n @ Names.ID =>
                  assertToken(n, p.nextToken(), VALUE_STRING)
                  id = Some(p.text())
                case n @ Names.INDEX =>
                  assertToken(n, p.nextToken(), VALUE_STRING)
                  index = Some(p.text())
                case n @ Names.TRUE_INDICES =>
                  assertToken(n, p.nextToken(), START_ARRAY)
                  trueIndices = Some(parseSparseBoolArray(p, 42))
                case n @ Names.TOTAL_INDICES =>
                  assertToken(n, p.nextToken(), VALUE_NUMBER)
                  totalIndices = Some(p.intValue())
                case n @ Names.VALUES =>
                  assertToken(n, p.nextToken(), START_ARRAY)
                  values = Some(parseFloatArray(p, 42))
                case _ => p.nextToken()
              }
            }
          case START_ARRAY =>
            isEmpty = false
            p.nextToken() match {
              case END_ARRAY =>
                values = Some(Array.empty)
              case VALUE_NUMBER =>
                values = Some(parseFloatArray(p, 42))
              case START_ARRAY =>
                trueIndices = Some(parseSparseBoolArray(p, 42))
                assertToken(p.nextToken(), VALUE_NUMBER)
                totalIndices = Some(p.intValue())
              case t =>
                throw new XContentParseException(unexpectedToken(t, SortedSet(END_ARRAY, START_ARRAY, VALUE_NUMBER)))
            }
          case _ =>
            throw new XContentParseException(unexpectedToken(p.currentToken(), SortedSet(START_ARRAY, START_OBJECT)))
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
            case _ =>
              throw new XContentParseException(unableToConstruct("vector"))
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
      if (p.currentToken() != START_OBJECT) {
        assertToken(p.nextToken(), START_OBJECT)
      }
      while (p.nextToken() == FIELD_NAME) {
        p.currentName() match {
          case n @ Names.TYPE =>
            assertToken(n, p.nextToken(), VALUE_STRING)
            assertValue(n, p.text(), Names.TYPES)
            typ = Some(p.text())
          case n @ Names.ELASTIKNN =>
            assertToken(n, p.nextToken(), START_OBJECT)
            while (p.nextToken() == FIELD_NAME) {
              p.currentName() match {
                case n @ Names.DIMS =>
                  assertToken(n, p.nextToken(), VALUE_NUMBER)
                  dims = Some(p.intValue())
                case n @ Names.LSH_L =>
                  assertToken(n, p.nextToken(), VALUE_NUMBER)
                  l = Some(p.intValue())
                case n @ Names.LSH_K =>
                  assertToken(n, p.nextToken(), VALUE_NUMBER)
                  k = Some(p.intValue())
                case n @ Names.LSH_W =>
                  assertToken(n, p.nextToken(), VALUE_NUMBER)
                  w = Some(p.intValue())
                case n @ Names.MODEL =>
                  assertToken(n, p.nextToken(), VALUE_STRING)
                  assertValue(n, p.text(), Names.MODELS)
                  model = Some(p.text())
                case n @ Names.REPEATING =>
                  assertToken(n, p.nextToken(), VALUE_BOOLEAN)
                  repeating = Some(p.booleanValue())
                case Names.SIMILARITY => similarity = Some(Decoder.similarity.decodeUnsafe(p))
                case _                => p.nextToken()
              }
            }
          case _ => p.nextToken()
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
        if (p.currentToken() != START_OBJECT) {
          assertToken(p.nextToken(), START_OBJECT)
        }
        while (p.nextToken() == FIELD_NAME) {
          p.currentName() match {
            case n @ Names.CANDIDATES =>
              assertToken(n, p.nextToken(), VALUE_NUMBER)
              candidates = Some(p.intValue())
            case n @ Names.FIELD =>
              assertToken(n, p.nextToken(), VALUE_STRING)
              field = Some(p.text())
            case n @ Names.MODEL =>
              assertToken(n, p.nextToken(), VALUE_STRING)
              assertValue(n, p.text(), Names.MODELS)
              model = Some(p.text())
            case n @ Names.PROBES =>
              assertToken(n, p.nextToken(), VALUE_NUMBER)
              probes = Some(p.intValue())
            case Names.SIMILARITY => similarity = Some(decodeUnsafe[Similarity](p))
            case Names.VEC        => vec = Some(decodeUnsafe[Vec](p))
            case _                => p.nextToken()
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

    val MODELS: SortedSet[String] = SortedSet(EXACT, LSH, PERMUTATION_LSH)
    val SIMILARITIES: SortedSet[String] = SortedSet(COSINE, HAMMING, JACCARD, L1, L2)
    val TYPES: SortedSet[String] = SortedSet(EKNN_SPARSE_BOOL_VECTOR, EKNN_DENSE_FLOAT_VECTOR)
  }
}