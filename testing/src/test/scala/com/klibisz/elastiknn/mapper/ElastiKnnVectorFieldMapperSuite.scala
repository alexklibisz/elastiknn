package com.klibisz.elastiknn.mapper

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.{ElastiKnnVector, ElasticAsyncClient}
import com.klibisz.elastiknn.elastic4s.scriptScoreQuery
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.{
  Index,
  Indexes,
  Response,
  XContentBuilder,
  XContentFactory
}
import com.sksamuel.elastic4s.requests.mappings.BasicField
import com.sksamuel.elastic4s.requests.script.{Script, ScriptType}
import com.sksamuel.elastic4s.requests.searches.{SearchRequest, SearchResponse}
import org.scalatest.{Assertion, AsyncFunSuite, Inspectors, Matchers, Succeeded}
import scalapb_circe.JsonFormat

import scala.concurrent.Future

class ElastiKnnVectorFieldMapperSuite
    extends AsyncFunSuite
    with Matchers
    with Inspectors
    with ElasticAsyncClient {

  private val fieldName = "ekv"
  private val field = BasicField(fieldName, "elastiknn_vector")

  test("create a mapping with type elastiknn_vector") {
    val indexName = "test-create-ekv-mapping"
    for {
      _ <- client.execute(deleteIndex(indexName))

      createRes <- client.execute(createIndex(indexName))
      _ = createRes.isSuccess shouldBe true

      mappingRes <- client.execute(putMapping(Indexes(indexName)).fields(field))
      _ <- mappingRes.isSuccess shouldBe true
    } yield Succeeded
  }

  test("index and search double vectors") {
    val indexName = "test-index-access-double-vectors"

    val doubleVectors = Seq(
      DoubleVector(Array(0.99, 0.12, -0.34)),
      DoubleVector(Array(0.22, 0.19, 0.44)),
      DoubleVector(Array(0.33, -0.119, 0.454))
    )

    val indexReqs =
      doubleVectors
        .map(
          v =>
            indexInto(indexName).source(
              XContentFactory.jsonBuilder
                .rawField(
                  fieldName,
                  JsonFormat.toJsonString(
                    ElastiKnnVector(ElastiKnnVector.Vector.DoubleVector(v))
                  ))
                .string()))

    def scriptSearch(i: Int, o: Int = 3): SearchRequest = {
      search(indexName).query(
        scriptScoreQuery(Script(
          s"""
           |double a = (double) doc[params.field][params.i];
           |int o = (int) params.o;
           |return a + o;
           |""".stripMargin,
          params = Map("o" -> o, "field" -> fieldName, "i" -> i)
        )))
    }

    def check(r: Response[SearchResponse], i: Int, o: Int = 3): Assertion =
      r.result.hits.hits
        .map(_.score)
        .sorted shouldBe doubleVectors
        .map(_.values(i).toFloat + o)
        .sorted
        .toArray

    for {
      _ <- client.execute(deleteIndex(indexName))

      createRes <- client.execute(createIndex(indexName))
      _ = createRes.isSuccess shouldBe true

      mappingRes <- client.execute(putMapping(Indexes(indexName)).fields(field))
      _ <- mappingRes.isSuccess shouldBe true

      indexRes <- client.execute(
        bulk(indexReqs).refresh(RefreshPolicy.IMMEDIATE))
      _ <- indexRes.isSuccess shouldBe true
      _ <- indexRes.result.errors shouldBe false

      scriptSearches = (0 until 3).map(i => client.execute(scriptSearch(i)))
      scriptResponses <- Future.sequence(scriptSearches)

      _ = forAll(scriptResponses) { _.isSuccess shouldBe true }
      _ = check(scriptResponses(0), 0)
      _ = check(scriptResponses(1), 1)
      _ = check(scriptResponses(2), 2)

    } yield Succeeded
  }
//
//  test("index and access bool vectors") {
//    ???
//  }

}
