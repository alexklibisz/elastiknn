package com.klibisz.elastiknn.api

import io.circe
import io.circe.{DecodingFailure, Json}
import org.scalatest.{Assertion, FunSuite, Matchers}

import scala.language.postfixOps

class ElasticsearchCodecSuite extends FunSuite with Matchers {

  implicit class CodecMatcher(s: String) {
    def shouldDecodeTo[T: ElasticsearchCodec](obj: T): Assertion = {

      val parsed: Either[circe.Error, Json] = ElasticsearchCodec.parse(s)
      val decoded: Either[circe.Error, T] = parsed.flatMap(ElasticsearchCodec.decodeJson[T])

      withClue("can't parse the given json string") {
        parsed shouldBe 'right
      }

      withClue("parsed json doesn't match encoded object") {
        parsed shouldBe Right(ElasticsearchCodec.encode(obj))
      }

      withClue("given json string doesn't decode to match the given object") {
        decoded shouldBe Right(obj)
      }
    }

    def shouldNotDecodeTo[T: ElasticsearchCodec]: Assertion = {
      val parsed = ElasticsearchCodec.parse(s)
      val tryDecode = parsed.flatMap(ElasticsearchCodec.decodeJson[T]).toTry
      assertThrows[DecodingFailure](tryDecode.get)
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

  test("mappings") {
    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "elastiknn": {
      |  "dims": 100
      | }
      |}
      |""".stripMargin shouldDecodeTo [Mapping] Mapping.SparseBool(100)

    """
      |{
      | "type": "elastiknn_dense_float_vector",
      | "elastiknn": {
      |  "dims": 100
      | }
      |}
      |""".stripMargin shouldDecodeTo [Mapping] Mapping.DenseFloat(100)

    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "elastiknn": {
      |  "dims": 100,
      |  "model": "lsh",
      |  "similarity": "jaccard",
      |  "bands": 99,
      |  "rows": 1
      | }
      |}
      |""".stripMargin shouldDecodeTo [Mapping] Mapping.JaccardLsh(100, 99, 1)

    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "elastiknn": {
      |  "dims": 100,
      |  "model": "sparse_indexed"
      | }
      |}
      |""".stripMargin shouldDecodeTo [Mapping] Mapping.SparseIndexed(100)

    """
      |{
      | "type": "elastiknn_dense_float_vector",
      | "elastiknn": {
      |  "dims": 100,
      |  "model": "sparse_indexed"
      | }
      |}
      |""".stripMargin.shouldNotDecodeTo[Mapping]
  }

  test("nearest neighbor queries") {

    import NearestNeighborsQuery._

    """
      |{
      | "field": "vec",
      | "model": "exact",
      | "similarity": "jaccard",
      | "vec": {
      |   "true_indices": [1,2,3],
      |   "total_indices": 99
      | }
      |}
      |""".stripMargin shouldDecodeTo [NearestNeighborsQuery] Exact("vec", Similarity.Jaccard, Vec.SparseBool(Array(1, 2, 3), 99))

    """
      |{
      | "field": "vec",
      | "model": "sparse_indexed",
      | "similarity": "hamming",
      | "vec": {
      |   "true_indices": [1,2,3],
      |   "total_indices": 99
      | }
      |}
      |""".stripMargin shouldDecodeTo [NearestNeighborsQuery] SparseIndexed("vec", Similarity.Hamming, Vec.SparseBool(Array(1, 2, 3), 99))

    """
      |{
      | "field": "vec",
      | "model": "sparse_indexed",
      | "similarity": "jaccard",
      | "vec": {
      |   "true_indices": [1,2,3],
      |   "total_indices": 99
      | }
      |}
      |""".stripMargin shouldDecodeTo [NearestNeighborsQuery] SparseIndexed("vec", Similarity.Jaccard, Vec.SparseBool(Array(1, 2, 3), 99))

    """
      |{
      | "field": "vec",
      | "model": "lsh",
      | "similarity": "jaccard",
      | "candidates": 100,
      | "vec": {
      |   "true_indices": [1,2,3],
      |   "total_indices": 99
      | }
      |}
      |""".stripMargin shouldDecodeTo [NearestNeighborsQuery] JaccardLsh("vec", 100, Vec.SparseBool(Array(1, 2, 3), 99))
  }
}
