package com.klibisz.elastiknn.api

import io.circe
import io.circe.{DecodingFailure, Json}
import org.scalatest.{Assertion, FunSuite, Matchers}

import scala.language.postfixOps

class ElasticsearchCodecSuite extends FunSuite with Matchers {

  implicit class CodecMatcher(s: String) {
    def decodesTo[T: ElasticsearchCodec](obj: T): Assertion = {

      lazy val parsed: Either[circe.Error, Json] = ElasticsearchCodec.parse(s)
      lazy val decoded: Either[circe.Error, T] = parsed.flatMap(ElasticsearchCodec.decodeJson[T])

      withClue("can't parse the given json string") {
        parsed shouldBe ('right)
      }

      withClue("parsed json doesn't match encoded object") {
        parsed shouldBe Right(ElasticsearchCodec.encode(obj))
      }

      withClue("given json string doesn't decode to match the given object") {
        decoded shouldBe Right(obj)
      }

      withClue("base64 encoding doesn't decode to match the given object") {
        val enc = ElasticsearchCodec.encodeB64(obj)
        ElasticsearchCodec.decodeB64(enc) shouldBe Right(obj)
      }
    }

    def shouldNotDecodeTo[T: ElasticsearchCodec]: Assertion = {
      val parsed = ElasticsearchCodec.parse(s)
      assertThrows[DecodingFailure](parsed.flatMap(ElasticsearchCodec.decodeJson[T]).toTry.get)
    }

  }

  test("vectors w/ valid contents") {
    """
      |{
      | "true_indices": [1,2,3],
      | "total_indices": 99
      |}
      |""".stripMargin decodesTo [Vec] Vec.SparseBool(Array(1, 2, 3), 99)

    """
      |{
      | "values": [0.1, 1, 11]
      |}
      |""".stripMargin decodesTo [Vec] Vec.DenseFloat(Array(0.1f, 1f, 11f))

    """
      |{
      |  "index": "foo",
      |  "id": "abc",
      |  "field": "vec"
      |}
      |""".stripMargin decodesTo [Vec] Vec.Indexed("foo", "abc", "vec")
  }

  test("vectors w/ invalid contents") {
    """
      |{
      |  "true_indices": [1,2,3]
      |}
      |""".stripMargin.shouldNotDecodeTo[Vec]

    """
      |{
      | "values": "foo"
      |}
      |""".stripMargin.shouldNotDecodeTo[Vec]

    """
      |{
      | "index": 99,
      | "id": "foo",
      | "field": "vec"
      |}
      |""".stripMargin.shouldNotDecodeTo[Vec]
  }

  test("mappings w/o models") {
    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "dims": 100
      |}
      |""".stripMargin decodesTo (Mapping.SparseBool(100, None): Mapping)

    """
      |{
      | "type": "elastiknn_dense_float_vector",
      | "dims": 100
      |}
      |""".stripMargin decodesTo [Mapping] Mapping.DenseFloat(100, None)

  }

  test("mappings w/ invalid types") {
    """
      |{
      | "type": "elastiknn_wrong",
      | "dims": 100
      |}
      |""".stripMargin.shouldNotDecodeTo[Mapping]

    """
      |{
      | "type": "",
      | "dims": 100
      |}
      |""".stripMargin.shouldNotDecodeTo[Mapping]
  }

  test("mappings w/ jaccard_indexed models") {
    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "dims": 100,
      | "model_options": {
      |  "type": "jaccard_indexed"
      | }
      |}
      |""".stripMargin decodesTo [Mapping] Mapping.SparseBool(100, Some(SparseBoolVectorModelOptions.JaccardIndexed))
  }

  test("mappings w/ jaccard_lsh models") {
    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "dims": 100,
      | "model_options": {
      |   "type": "jaccard_lsh",
      |   "bands": 99,
      |   "rows": 1
      | }
      |}
      |""".stripMargin decodesTo [Mapping] Mapping.SparseBool(100, Some(SparseBoolVectorModelOptions.JaccardLsh(99, 1)))
  }

  test("mappings w/ invalid models") {
    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "dims": 100,
      | "model_options": {
      |  "type": "jaccard_index"
      | }
      |}
      |""".stripMargin.shouldNotDecodeTo[Mapping]

    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "dims": 100,
      | "model_options": { }
      |}
      |""".stripMargin.shouldNotDecodeTo[Mapping]
  }

  test("query options") {
    """
      |{
      | "type": "exact",
      | "similarity": "jaccard"
      |}
      |""".stripMargin decodesTo [QueryOptions] QueryOptions.Exact(Similarity.Jaccard)

    """
      |{
      | "type": "jaccard_indexed"
      |}
      |""".stripMargin.decodesTo[QueryOptions](QueryOptions.JaccardIndexed)

    """
      |{
      | "type": "jaccard_lsh",
      | "candidates": 99,
      | "refine": true
      |}
      |""".stripMargin decodesTo [QueryOptions] QueryOptions.JaccardLsh(99, true)
  }

}
