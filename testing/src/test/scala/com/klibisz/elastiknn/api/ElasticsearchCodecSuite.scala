package com.klibisz.elastiknn.api

import io.circe
import io.circe.{DecodingFailure, Json}
import org.scalatest.{Assertion, FunSuite, Matchers}

import scala.language.postfixOps

class ElasticsearchCodecSuite extends FunSuite with Matchers {

  implicit class CodecMatcher(s: String) {
    def shouldDecodeTo[T: ElasticsearchCodec](obj: T): Assertion = {

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
      |""".stripMargin shouldDecodeTo [Vec] Vec.SparseBool(Array(1, 2, 3), 99)

    """
      |{
      | "values": [0.1, 1, 11]
      |}
      |""".stripMargin shouldDecodeTo [Vec] Vec.DenseFloat(Array(0.1f, 1f, 11f))

    """
      |{
      |  "index": "foo",
      |  "id": "abc",
      |  "field": "vec"
      |}
      |""".stripMargin shouldDecodeTo [Vec] Vec.Indexed("foo", "abc", "vec")
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

//  test("mappings w/o models") {
//    """
//      |{
//      | "type": "elastiknn_sparse_bool_vector",
//      | "dims": 100
//      |}
//      |""".stripMargin shouldDecodeTo [Mapping] Mapping.SparseBoolOld(100, None)
//
//    """
//      |{
//      | "type": "elastiknn_dense_float_vector",
//      | "dims": 100
//      |}
//      |""".stripMargin shouldDecodeTo [Mapping] Mapping.DenseFloatOld(100, None)
//
//  }
//
//  test("mappings w/ invalid types") {
//    """
//      |{
//      | "type": "elastiknn_wrong",
//      | "dims": 100
//      |}
//      |""".stripMargin.shouldNotDecodeTo[Mapping]
//
//    """
//      |{
//      | "type": "",
//      | "dims": 100
//      |}
//      |""".stripMargin.shouldNotDecodeTo[Mapping]
//  }
//
//  test("mappings w/ jaccard_indexed models") {
//    """
//      |{
//      | "type": "elastiknn_sparse_bool_vector",
//      | "dims": 100,
//      | "model_options": {
//      |  "type": "jaccard_indexed"
//      | }
//      |}
//      |""".stripMargin shouldDecodeTo [Mapping] Mapping.SparseBoolOld(100, Some(SparseBoolModelOptions.JaccardIndexed))
//  }
//
//  test("mappings w/ jaccard_lsh models") {
//    """
//      |{
//      | "type": "elastiknn_sparse_bool_vector",
//      | "dims": 100,
//      | "model_options": {
//      |   "type": "jaccard_lsh",
//      |   "bands": 99,
//      |   "rows": 1
//      | }
//      |}
//      |""".stripMargin shouldDecodeTo [Mapping] Mapping.SparseBoolOld(100, Some(SparseBoolModelOptions.JaccardLsh(99, 1)))
//  }
//
//  test("mappings w/ invalid models") {
//    """
//      |{
//      | "type": "elastiknn_sparse_bool_vector",
//      | "dims": 100,
//      | "model_options": {
//      |  "type": "jaccard_index"
//      | }
//      |}
//      |""".stripMargin.shouldNotDecodeTo[Mapping]
//
//    """
//      |{
//      | "type": "elastiknn_sparse_bool_vector",
//      | "dims": 100,
//      | "model_options": { }
//      |}
//      |""".stripMargin.shouldNotDecodeTo[Mapping]
//  }

  test("mappings") {
    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "dims": 100
      |}
      |""".stripMargin shouldDecodeTo [Mapping] Mapping.SparseBool(100)

    """
      |{
      | "type": "elastiknn_dense_float_vector",
      | "dims": 100
      |}
      |""".stripMargin shouldDecodeTo [Mapping] Mapping.DenseFloat(100)

    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "dims": 100,
      | "model": "lsh",
      | "similarity": "jaccard",
      | "bands": 99,
      | "rows": 1
      |}
      |""".stripMargin shouldDecodeTo [Mapping] Mapping.JaccardLsh(100, 99, 1)

    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "dims": 100,
      | "model": "sparse_indexed"
      |}
      |""".stripMargin shouldDecodeTo [Mapping] Mapping.SparseIndexed(100)

    """
      |{
      | "type": "elastiknn_dense_float_vector",
      | "dims": 100,
      | "model": "sparse_indexed"
      |}
      |""".stripMargin.shouldNotDecodeTo[Mapping]
  }

  test("nearest neighbor queries (revised)") {
    """
      |{
      | "field": "vec",
      | "type": "exact",
      | "similarity": "jaccard",
      | "vector": {
      |   "true_indices": [1,2,3],
      |   "total_indices": 99
      | }
      |}
      |""".stripMargin

    """
      |{
      | "field": "vec",
      | "model": "sparse_indexed",
      | "similarity": "jaccard",
      | "vector": {
      |   "true_indices": [1,2,3],
      |   "total_indices": 99
      | }
      |}
      |""".stripMargin

    """
      |{
      | "field": "vec",
      | "type": "lsh",
      | "similarity": "jaccard",
      | "candidates": 100,
      | "refine": true,
      | "vector": {
      |   "true_indices": [1,2,3],
      |   "total_indices": 99
      | }
      |}
      |""".stripMargin
  }

//  test("query options") {
//    """
//      |{
//      | "type": "exact",
//      | "similarity": "jaccard"
//      |}
//      |""".stripMargin shouldDecodeTo [QueryOptions] QueryOptions.Exact(Similarity.Jaccard)
//
//    """
//      |{
//      | "type": "jaccard_indexed"
//      |}
//      |""".stripMargin.shouldDecodeTo[QueryOptions](QueryOptions.JaccardIndexed)
//
//    """
//      |{
//      | "type": "jaccard_lsh",
//      | "candidates": 99,
//      | "refine": true
//      |}
//      |""".stripMargin shouldDecodeTo [QueryOptions] QueryOptions.JaccardLsh(99, true)
//  }

}
