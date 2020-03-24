package com.klibisz.elastiknn.api

import io.circe
import io.circe.Json
import org.scalatest.{Assertion, FunSuite, Matchers}

class ElasticsearchCodecSuite extends FunSuite with Matchers {

  implicit class CodecMatcher(s: String) {
    def matches[T: ElasticsearchCodec](obj: T): Assertion = {

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
  }

  test("mappings w/o models") {
    """
      |{
      | "type": "elastiknn_sparse_bool_vector",
      | "dims": 100
      |}
      |""".stripMargin matches (Mapping.SparseBoolVector(100, None): Mapping)

    """
      |{
      | "type": "elastiknn_dense_float_vector",
      | "dims": 100
      |}
      |""".stripMargin matches (Mapping.DenseFloatVector(100, None): Mapping)

  }

//  test("mappings w/ invalid types") {}
//  test("mappings w/ jaccard_indexed models") {}
//  test("mappings w/ jaccard_lsh models") {}

}
