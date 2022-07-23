package com.klibisz.elastiknn.api

import org.elasticsearch.xcontent.XContentParseException
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

  private def shuffle(original: Json): Json = original.asObject match {
    case Some(obj) =>
      val entries = obj.toIterable
      val shuffledChildren = entries.map { case (k, v) => (k, shuffle(v)) }
      val shuffledThisLevel = rng.shuffle(shuffledChildren).toList
      val shuffled = Json.obj(shuffledThisLevel: _*)
      shuffled
    case None => original
  }

  private def addExtraKeys(original: Json): Json = original.asObject match {
    case Some(_) =>
      val junk = Json.obj(
        "extra_int" -> Json.fromInt(rng.nextInt()),
        "extra_string" -> Json.fromString(rng.nextString(rng.nextInt(10)))
      )
      original.deepMerge(junk)
    case None => original
  }

  private def toString(j: Json): String = {
    val d = rng.nextFloat()
    if (d < 0.2) shuffle(j).noSpaces
    else if (d < 0.4) shuffle(j).spaces2
    else if (d < 0.6) shuffle(j).spaces4
    else if (d < 0.8) shuffle(j).spaces2SortKeys
    else shuffle(j).spaces4SortKeys
  }

  private def randomize(j: Json, addExtraKeys: Boolean): String =
    if (addExtraKeys) toString(shuffle(this.addExtraKeys(j))) else toString(shuffle(j))

  private def roundtrip[T: XContentCodec.Encoder: XContentCodec.Decoder](
      expectedJson: Json,
      expectedObject: T,
      addExtraKeys: Boolean = true
  ): Assertion = {
    // Encode with XContent.
    val encoded: String = XContentCodec.encodeUnsafeToString(expectedObject)
    // Check that the string matches the expected JSON encoded to a string w/ sorted keys and no spaces.
    encoded shouldBe expectedJson.noSpacesSortKeys
    // Re-order the keys and possibly add extra keys.
    val randomized = randomize(expectedJson, addExtraKeys)
    // Decode the string back to an object.
    val decoded: T = XContentCodec.decodeUnsafeFromString(randomized)
    decoded shouldBe expectedObject
  }

  private def decode[T: XContentCodec.Decoder](expectedJson: Json, expectedObject: T, addExtraKeys: Boolean = false): Assertion = {
    val decoded = decodeUnsafeFromString[T](randomize(expectedJson, addExtraKeys))
    decoded shouldBe expectedObject
  }

  "test utilities" - {
    "randomize" in {
      import io.circe.syntax._
      import io.circe.parser
      val originalObject = Json.obj("foo" -> "1".asJson, "nest" -> Json.obj("foo" -> "1".asJson, "bar" -> "2".asJson)).asJson
      val shuffled = (0 to 100).map(_ => randomize(originalObject, false))
      // If you just look at the strings, there should be several variations.
      shuffled.distinct.length shouldBe >=(5)
      // If you parse the strings back to Json objects, there should only be one, the original object.
      shuffled.map(parser.parse).distinct shouldBe List(Right(originalObject))
    }
    "randomize with extra keys" in {
      val originalObject = Json.obj("foo" -> "1".asJson, "nest" -> Json.obj("foo" -> "1".asJson, "bar" -> "2".asJson)).asJson
      val shuffled = (0 to 100).map(_ => randomize(originalObject, true))
      // Both strings and Json objects should vary, as we've added extra keys.
      shuffled.distinct.length shouldBe >=(5)
      shuffled.map(parser.parse(_).fold(fail(_), identity)).distinct.length shouldBe >=(5)
    }
  }

  "fails to decode vector with extra keys in it" in {
    val vec = Vec.DenseFloat.random(10)
    val string = s"""{ "extra": "junk", "values": ${vec.values.asJson.noSpaces}, "more": "junk" }"""
    val result = decodeUnsafeFromString[Vec](string)
    result shouldBe vec
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
      ex1.getMessage shouldBe "Expected token to be one of [cosine,hamming,jaccard,l1,l2] but found [wrong]"
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
    "errors" in {
      val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Vec]("""{"values":["wrong"]}"""))
      ex1.getMessage shouldBe "Expected token to be one of [VALUE_NUMBER] but found [VALUE_STRING]"
      val ex2 = intercept[XContentParseException](decodeUnsafeFromString[Vec](s"""["wrong"]"""))
      ex2.getMessage shouldBe "Expected token to be one of [END_ARRAY,START_ARRAY,VALUE_NUMBER] but found [VALUE_STRING]"
      val ex3 = intercept[XContentParseException](decodeUnsafeFromString[Vec.DenseFloat](s"""[[1,2,3],10]"""))
      ex3.getMessage shouldBe "Unable to construct [dense float vector] from parsed JSON"
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
    "errors" in {
      val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Vec]("""{"true_indices":10,"total_indices":[0,1,2]}"""))
      ex1.getMessage shouldBe "Expected [true_indices] to be one of [START_ARRAY] but found [VALUE_NUMBER]"
      val ex2 = intercept[XContentParseException](decodeUnsafeFromString[Vec](s"""[10,[1,2,3]]"""))
      ex2.getMessage shouldBe "Expected token to be one of [VALUE_NUMBER] but found [START_ARRAY]"
      val ex3 = intercept[XContentParseException](decodeUnsafeFromString[Vec.DenseFloat](s"""[[1,2,3],10]"""))
      ex3.getMessage shouldBe "Unable to construct [dense float vector] from parsed JSON"
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
    "errors" in {
      val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Vec]("""{"id":"...","field":"...","index":99}"""))
      ex1.getMessage shouldBe "Expected [index] to be one of [VALUE_STRING] but found [VALUE_NUMBER]"
    }
  }

  "Vec.Empty" - {
    "roundtrip" in {
      roundtrip(Json.obj(), Vec.Empty(), addExtraKeys = false)
    }
    "errors" in {
      val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Vec]("""{"...":"..."}"""))
      ex1.getMessage shouldBe "Unable to construct [vector] from parsed JSON"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_dense_float_vector",
            | "elastiknn": [1,2,3]
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Expected [elastiknn] to be one of [START_OBJECT] but found [START_ARRAY]"
        val ex2 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_dense_float_vector",
            | "elastiknn": {
            |  "model": "wrong!"
            | }
            |}
            |""".stripMargin))
        ex2.getMessage shouldBe "Expected [model] to be one of [exact,lsh,permutation_lsh] but found [wrong!]"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_sparse_vector",
            | "elastiknn": [1,2,3]
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Expected [type] to be one of [elastiknn_dense_float_vector,elastiknn_sparse_bool_vector] but found [elastiknn_sparse_vector]"
        val ex2 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_sparse_bool_vector",
            | "elastiknn": {}
            |}
            |""".stripMargin))
        ex2.getMessage shouldBe "Unable to construct [mapping] from parsed JSON"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_sparse_bool_vector",
            | "elastiknn": {
            |   "model": "lsh",
            |   "dims": 33,
            |   "similarity": "jaccard",
            |   "L": "33",
            |   "k": 3
            | }
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Expected [L] to be one of [VALUE_NUMBER] but found [VALUE_STRING]"
        val ex2 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_sparse_bool_vector",
            | "elastiknn": {
            |   "model": "lsh",
            |   "dims": 33,
            |   "similarity": "jacard",
            |   "L": 33,
            |   "k": 3
            | }
            |}
            |""".stripMargin))
        ex2.getMessage shouldBe "Expected token to be one of [cosine,hamming,jaccard,l1,l2] but found [jacard]"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_sparse_bool_vector",
            | "elastiknn": {
            |   "model": "lsh",
            |   "dims": 33,
            |   "similarity": "hamming",
            |   "L": "33",
            |   "k": 3
            | }
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Expected [L] to be one of [VALUE_NUMBER] but found [VALUE_STRING]"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_dense_float_vector",
            | "elastiknn": {
            |   "model": "lsh",
            |   "dims": 33,
            |   "similarity": "cosine",
            |   "L": "33",
            |   "k": 3
            | }
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Expected [L] to be one of [VALUE_NUMBER] but found [VALUE_STRING]"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_dense_float_vector",
            | "elastiknn": {
            |   "model": "lsh",
            |   "dims": 33,
            |   "similarity": "l2",
            |   "L": "33",
            |   "k": 3
            | }
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Expected [L] to be one of [VALUE_NUMBER] but found [VALUE_STRING]"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[Mapping]("""
            |{ 
            | "type": "elastiknn_dense_float_vector",
            | "elastiknn": {
            |   "model": "permutation_lsh",
            |   "repeating": "true"
            | }
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Expected [repeating] to be one of [VALUE_BOOLEAN] but found [VALUE_STRING]"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[NearestNeighborsQuery]("""
            |{ 
            | "field": "vec",
            | "model": "exact",
            | "similarity": "jaccard",
            | "vec": [0,[1,2,3]]
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Expected token to be one of [VALUE_NUMBER] but found [START_ARRAY]"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[NearestNeighborsQuery]("""
            |{ 
            | "field": "vec",
            | "model": "lsh",
            | "similarity": "cosine",
            | "vec": [0, 1, 2]
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Unable to construct [nearest neighbors query] from parsed JSON"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[NearestNeighborsQuery]("""
            |{ 
            | "field": "vec",
            | "model": "lsh",
            | "similarity": "hamming",
            | "vec": [0, 1, 2]
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Unable to construct [nearest neighbors query] from parsed JSON"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[NearestNeighborsQuery]("""
            |{ 
            | "field": "vec",
            | "model": "lsh",
            | "similarity": "jaccard",
            | "vec": [0, 1, 2]
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Unable to construct [nearest neighbors query] from parsed JSON"
      }
    }
    "L2Lsh" - {
      "roundtrip" in {
        for {
          _ <- 1 to 100
          vec = randomVec()
          query = NearestNeighborsQuery.L2Lsh(s"field${rng.nextInt()}", rng.nextInt(), rng.nextInt(), vec)
          expected = Json.obj(
            "field" -> query.field.asJson,
            "candidates" -> query.candidates.asJson,
            "probes" -> query.probes.asJson,
            "model" -> "lsh".asJson,
            "similarity" -> "l2".asJson,
            "vec" -> parse(XContentCodec.encodeUnsafeToString(vec)).fold(fail(_), identity)
          )
        } {
          roundtrip[NearestNeighborsQuery](expected, query)
        }
      }
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[NearestNeighborsQuery]("""
            |{ 
            | "field": "vec",
            | "model": "lsh",
            | "similarity": "l2",
            | "candidates": 22,
            | "vec": [0, 1, 2]
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Unable to construct [nearest neighbors query] from parsed JSON"
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
      "errors" in {
        val ex1 = intercept[XContentParseException](decodeUnsafeFromString[NearestNeighborsQuery]("""
            |{ 
            | "field": "vec",
            | "model": "permutation_lsh",
            | "similarity": "l2",
            | "vec": [0, 1, 2]
            |}
            |""".stripMargin))
        ex1.getMessage shouldBe "Unable to construct [nearest neighbors query] from parsed JSON"
      }
    }
  }
}
