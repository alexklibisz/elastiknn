package com.klibisz.elastiknn.api

import org.elasticsearch.common.xcontent.XContentParseException
import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

// Use Circe for encoding the expected JSON structures.
import io.circe._
import io.circe.syntax._
import io.circe.parser.parse

import scala.util.Random

class XContentCodecSuite extends AnyFreeSpec with Matchers {

  import XContentCodec._

  private implicit val rng: Random = new Random(0)

  private def shuffle(j: Json): Json = j.asObject match {
    case Some(obj) =>
      val entries = obj.toIterable
      val shuffledValues = entries.map { case (k, v) => (k, shuffle(v)) }
      val shuffledKeys = rng.shuffle(shuffledValues).toList
      Json.obj(shuffledKeys: _*)
    case None => j
  }

  private def randomize(j: Json): String = {
    val d = rng.nextFloat()
    if (d < 0.2) shuffle(j).noSpaces
    else if (d < 0.4) shuffle(j).spaces2
    else if (d < 0.6) shuffle(j).spaces4
    else if (d < 0.8) shuffle(j).spaces2SortKeys
    else shuffle(j).spaces4SortKeys
  }

  private def roundtrip[T: XContentCodec.Encoder: XContentCodec.Decoder: ElasticsearchCodec](expected: Json, t: T): Assertion = {
    // Encode with XContent and check that it matches the expected JSON w/ sorted keys and no spaces.
    val encodedXContent: String = XContentCodec.encodeUnsafeToString(t)
    encodedXContent shouldBe expected.noSpacesSortKeys
    // Encode with Circe.
    val encodedCirce: Json = ElasticsearchCodec.encode(t)
    // Test roundtrip between the two codecs.
    // Encode -> Decode
    // Circe -> Circe
    val decodedCirceCirce = ElasticsearchCodec.decodeJsonGet[T](encodedCirce)
    decodedCirceCirce shouldBe t
    // Circe -> XContent
    val decodedCirceXContent = XContentCodec.decodeUnsafeFromString(encodedCirce.noSpaces)
    decodedCirceXContent shouldBe t
    // XContent -> Circe
    val decodedXContentCirce = ElasticsearchCodec.decodeJsonGet[T](ElasticsearchCodec.parseGet(encodedXContent))
    decodedXContentCirce shouldBe t
    // XContent -> XContent
    val decodedXContentXContent = XContentCodec.decodeUnsafeFromString(randomize(expected))
    decodedXContentXContent shouldBe t
  }

  private def decode[T: XContentCodec.Decoder](expected: Json, t: T): Assertion = {
    val decoded = decodeUnsafeFromString[T](randomize(expected))
    decoded shouldBe t
  }

  "test utilities" - {
    "shuffled" in {
      val j = Json.obj("foo" -> "1".asJson, "nest" -> Json.obj("foo" -> "1".asJson, "bar" -> "2".asJson)).asJson
      val shuffled = (0 to 100).map(_ => randomize(j))
      shuffled.distinct.length shouldBe >=(5)
    }
  }

  "Similarity" - {
    "roundtrip" in {
      for {
        (str, sim: Similarity) <- Seq(
          ("jaccard", Similarity.Jaccard),
          ("Jaccard", Similarity.Jaccard),
          ("JACCARD", Similarity.Jaccard),
          ("hamming", Similarity.Hamming),
          ("Hamming", Similarity.Hamming),
          ("HAMMING", Similarity.Hamming),
          ("l1", Similarity.L1),
          ("L1", Similarity.L1),
          ("l2", Similarity.L2),
          ("L2", Similarity.L2),
          ("cosine", Similarity.Cosine),
          ("Cosine", Similarity.Cosine),
          ("COSINE", Similarity.Cosine)
        )
      } roundtrip[Similarity](Json.fromString(str.toLowerCase), sim)
    }
    "errors" in {
      val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Similarity]("\"wrong\""))
      ex1.getMessage shouldBe "Unexpected value [wrong]"
      val ex2 = intercept[XContentParseException](decodeUnsafeFromString[Similarity]("99"))
      ex2.getMessage shouldBe "Expected token to be one of [VALUE_STRING] but found [VALUE_NUMBER]"
    }
  }

  "Vec.DenseFloat" - {
    "roundtrip" in {
      for {
        i <- 0 to 100
        v = Vec.DenseFloat.random(i)
        expected = Json.obj("values" -> v.values.asJson)
      } {
        roundtrip(expected, v)
      }
    }
    "roundtrip (shorthand)" in {
      for {
        i <- 0 to 100
        v = Vec.DenseFloat.random(i)
        expected = v.values.asJson
      } {
        decode(expected, v)
      }
    }
  }

  "Vec.SparseBool" - {
    "roundtrip" in {
      for {
        i <- 0 to 100
        v = Vec.SparseBool.random(i)
        expected = Json.obj("total_indices" -> v.totalIndices.asJson, "true_indices" -> v.trueIndices.asJson)
      } {
        roundtrip(expected, v)
      }
    }
    "decode shorthand" in {
      for {
        i <- 0 to 100
        v = Vec.SparseBool.random(i)
        expected = Json.arr(v.trueIndices.asJson, v.totalIndices.asJson)
      } {
        decode(expected, v)
      }
    }
  }

  "Vec.Indexed" - {
    "roundtrip" in {
      for {
        i <- 0 to 100
        index = s"index-$i"
        id = s"id-$i"
        field = s"field-$i"
        v = Vec.Indexed(index, id, field)
        expected = Json.obj("index" -> v.index.asJson, "id" -> v.id.asJson, "field" -> v.field.asJson)
      } {
        roundtrip(expected, v)
      }
    }
  }

  "Vec.Empty" - {
    "roundtrip" in {
      roundtrip(Json.obj(), Vec.Empty())
    }
  }

  "Mapping" - {
    "DenseFloat" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          dims = rng.nextInt()
          mapping = Mapping.DenseFloat(dims)
          expected = Json.obj(
            "type" -> "elastiknn_dense_float_vector".asJson,
            "elastiknn" -> Json.obj(
              "model" -> "exact".asJson,
              "dims" -> dims.asJson
            )
          )
        } {
          roundtrip[Mapping](expected, mapping)
        }
      }
    }
    "SparseBool" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          dims = rng.nextInt()
          mapping = Mapping.SparseBool(dims)
          expected = Json.obj(
            "type" -> "elastiknn_sparse_bool_vector".asJson,
            "elastiknn" -> Json.obj(
              "model" -> "exact".asJson,
              "dims" -> dims.asJson
            )
          )
        } {
          roundtrip[Mapping](expected, mapping)
        }
      }
    }
    "JaccardLsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          (dims, l, k) = (rng.nextInt(), rng.nextInt(), rng.nextInt())
          mapping = Mapping.JaccardLsh(dims, l, k)
          expected = Json.obj(
            "type" -> "elastiknn_sparse_bool_vector".asJson,
            "elastiknn" -> Json.obj(
              "model" -> "lsh".asJson,
              "dims" -> dims.asJson,
              "similarity" -> "jaccard".asJson,
              "L" -> l.asJson,
              "k" -> k.asJson
            )
          )
        } {
          roundtrip[Mapping](expected, mapping)
        }
      }
    }
    "HammingLsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          (dims, l, k) = (rng.nextInt(), rng.nextInt(), rng.nextInt())
          mapping = Mapping.HammingLsh(dims, l, k)
          expected = Json.obj(
            "type" -> "elastiknn_sparse_bool_vector".asJson,
            "elastiknn" -> Json.obj(
              "model" -> "lsh".asJson,
              "dims" -> dims.asJson,
              "similarity" -> "hamming".asJson,
              "L" -> l.asJson,
              "k" -> k.asJson
            )
          )
        } {
          roundtrip[Mapping](expected, mapping)
        }
      }
    }
    "CosineLsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          (dims, l, k) = (rng.nextInt(), rng.nextInt(), rng.nextInt())
          mapping = Mapping.CosineLsh(dims, l, k)
          expected = Json.obj(
            "type" -> "elastiknn_dense_float_vector".asJson,
            "elastiknn" -> Json.obj(
              "model" -> "lsh".asJson,
              "dims" -> dims.asJson,
              "similarity" -> "cosine".asJson,
              "L" -> l.asJson,
              "k" -> k.asJson
            )
          )
        } {
          roundtrip[Mapping](expected, mapping)
        }
      }
    }
    "L2Lsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          (dims, l, k, w) = (rng.nextInt(), rng.nextInt(), rng.nextInt(), rng.nextInt())
          mapping = Mapping.L2Lsh(dims, l, k, w)
          expected = Json.obj(
            "type" -> "elastiknn_dense_float_vector".asJson,
            "elastiknn" -> Json.obj(
              "model" -> "lsh".asJson,
              "dims" -> dims.asJson,
              "similarity" -> "l2".asJson,
              "L" -> l.asJson,
              "k" -> k.asJson,
              "w" -> w.asJson
            )
          )
        } {
          roundtrip[Mapping](expected, mapping)
        }
      }
    }
    "PermutationLsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          (dims, k, repeating) = (rng.nextInt(), rng.nextInt(), rng.nextBoolean())
          mapping = Mapping.PermutationLsh(dims, k, repeating)
          expected = Json.obj(
            "type" -> "elastiknn_dense_float_vector".asJson,
            "elastiknn" -> Json.obj(
              "model" -> "permutation_lsh".asJson,
              "dims" -> dims.asJson,
              "k" -> k.asJson,
              "repeating" -> repeating.asJson
            )
          )
        } {
          roundtrip[Mapping](expected, mapping)
        }
      }
    }
  }

  "NearestNeighborsQuery" - {
    val vecChoices = Array(
      () => Vec.DenseFloat.random(rng.nextInt(100)),
      () => Vec.Indexed(s"index${rng.nextInt()}", s"id${rng.nextInt()}", s"field${rng.nextInt()}"),
      () => Vec.SparseBool.random(rng.nextInt(1000)),
      () => Vec.Empty()
    )

    def randomVec(): Vec = vecChoices(rng.nextInt(vecChoices.length))()

    def randomSimilarity(): Similarity = Similarity.values(rng.nextInt(Similarity.values.length))

    "Exact" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          vec = randomVec()
          sim = randomSimilarity()
          query = NearestNeighborsQuery.Exact(s"field${rng.nextInt()}", sim, vec)
          expected = Json.obj(
            "field" -> query.field.asJson,
            "model" -> "exact".asJson,
            "similarity" -> parse(XContentCodec.encodeUnsafeToString(sim)).fold(fail(_), identity),
            "vec" -> parse(XContentCodec.encodeUnsafeToString(vec)).fold(fail(_), identity)
          )
        } {
          roundtrip[NearestNeighborsQuery](expected, query)
        }
      }
    }
    "CosineLsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          vec = randomVec()
          query = NearestNeighborsQuery.CosineLsh(s"field${rng.nextInt()}", rng.nextInt(), vec)
          expected = Json.obj(
            "field" -> query.field.asJson,
            "candidates" -> query.candidates.asJson,
            "model" -> "lsh".asJson,
            "similarity" -> "cosine".asJson,
            "vec" -> parse(XContentCodec.encodeUnsafeToString(vec)).fold(fail(_), identity)
          )
        } {
          roundtrip[NearestNeighborsQuery](expected, query)
        }
      }
    }
    "HammingLsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          vec = randomVec()
          query = NearestNeighborsQuery.HammingLsh(s"field${rng.nextInt()}", rng.nextInt(), vec)
          expected = Json.obj(
            "field" -> query.field.asJson,
            "candidates" -> query.candidates.asJson,
            "model" -> "lsh".asJson,
            "similarity" -> "hamming".asJson,
            "vec" -> parse(XContentCodec.encodeUnsafeToString(vec)).fold(fail(_), identity)
          )
        } {
          roundtrip[NearestNeighborsQuery](expected, query)
        }
      }
    }
    "JaccardLsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          vec = randomVec()
          query = NearestNeighborsQuery.JaccardLsh(s"field${rng.nextInt()}", rng.nextInt(), vec)
          expected = Json.obj(
            "field" -> query.field.asJson,
            "candidates" -> query.candidates.asJson,
            "model" -> "lsh".asJson,
            "similarity" -> "jaccard".asJson,
            "vec" -> parse(XContentCodec.encodeUnsafeToString(vec)).fold(fail(_), identity)
          )
        } {
          roundtrip[NearestNeighborsQuery](expected, query)
        }
      }
    }
    "PermutationLsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          vec = randomVec()
          sim = randomSimilarity()
          query = NearestNeighborsQuery.PermutationLsh(s"field${rng.nextInt()}", sim, rng.nextInt(), vec)
          expected = Json.obj(
            "field" -> query.field.asJson,
            "candidates" -> query.candidates.asJson,
            "model" -> "permutation_lsh".asJson,
            "similarity" -> parse(XContentCodec.encodeUnsafeToString(sim)).fold(fail(_), identity),
            "vec" -> parse(XContentCodec.encodeUnsafeToString(vec)).fold(fail(_), identity)
          )
        } {
          roundtrip[NearestNeighborsQuery](expected, query)
        }
      }
    }
  }
}
