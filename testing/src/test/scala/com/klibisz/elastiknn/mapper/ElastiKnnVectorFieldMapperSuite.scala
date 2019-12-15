package com.klibisz.elastiknn.mapper

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.client.ElastiKnnDsl
import com.klibisz.elastiknn.utils.Implicits._
import com.sksamuel.elastic4s.requests
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.CreateIndexResponse
import com.sksamuel.elastic4s.requests.mappings.BasicField
import com.sksamuel.elastic4s.requests.searches.{SearchRequest, SearchResponse}
import com.sksamuel.elastic4s.{Indexes, Response, XContentFactory}
import org.scalatest._
import scalapb_circe.JsonFormat

import scala.concurrent.Future

class ElastiKnnVectorFieldMapperSuite
    extends AsyncFunSuite
    with Matchers
    with Inspectors
    with Elastic4sMatchers
    with ElasticAsyncClient
    with ElastiKnnDsl {

  private val fieldName = "ekv"
  private val field = BasicField(fieldName, "elastiknn_vector")

  test("create a mapping with type elastiknn_vector") {
    val indexName = "test-create-ekv-mapping"
    for {
      _ <- client.execute(deleteIndex(indexName))

      createRes: Response[CreateIndexResponse] <- client.execute(createIndex(indexName))
      _ = createRes.shouldBeSuccess

      mappingRes <- client.execute(putMapping(Indexes(indexName)).fields(field))
      _ <- mappingRes.shouldBeSuccess
    } yield Succeeded
  }

  test("index and script search double vectors") {
    val indexName = "test-index-script-search-double-vectors"

    val floatVectors = Seq(
      FloatVector(Array(0.99, 0.12, -0.34)),
      FloatVector(Array(0.22, 0.19, 0.44)),
      FloatVector(Array(0.33, -0.119, 0.454))
    )

    val offset = 3

    val indexReqs =
      floatVectors
        .map(
          v =>
            indexInto(indexName).source(
              XContentFactory.jsonBuilder
                .rawField(fieldName,
                          JsonFormat.toJsonString(
                            ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(v))
                          ))
                .string()))

    def scriptSearch(i: Int): SearchRequest = {
      search(indexName).query(
        scriptScoreQuery(requests.script.Script(
          s"""
           |double a = (double) doc[params.field][params.i];
           |int o = (int) params.o;
           |return a + o;
           |""".stripMargin,
          params = Map("o" -> offset, "field" -> fieldName, "i" -> i)
        )))
    }

    def check(r: Response[SearchResponse], i: Int): Assertion =
      r.result.hits.hits
        .map(_.score)
        .sorted shouldBe floatVectors
        .map(_.values(i).toFloat + offset)
        .sorted
        .toArray

    for {
      _ <- client.execute(deleteIndex(indexName))

      createRes <- client.execute(createIndex(indexName))
      _ = createRes.shouldBeSuccess

      mappingRes <- client.execute(putMapping(Indexes(indexName)).fields(field))
      _ <- mappingRes.shouldBeSuccess

      indexRes <- client.execute(bulk(indexReqs).refresh(RefreshPolicy.IMMEDIATE))
      _ = indexRes.shouldBeSuccess
      _ = indexRes.result.errors shouldBe false

      scriptSearches = floatVectors.indices.map(i => client.execute(scriptSearch(i)))
      scriptResponses <- Future.sequence(scriptSearches)

      _ = forAll(scriptResponses) { _.shouldBeSuccess }
      _ = check(scriptResponses(0), 0)
      _ = check(scriptResponses(1), 1)
      _ = check(scriptResponses(2), 2)

    } yield Succeeded
  }

  test("index and script search bool vectors") {
    val indexName = "test-index-script-search-bool-vectors"

    val boolVectors = Seq(
      SparseBoolVector.from(Array(true, false, false)),
      SparseBoolVector.from(Array(false, false, true)),
      SparseBoolVector.from(Array(true, true, true))
    )

    val indexReqs =
      boolVectors
        .map(
          v =>
            indexInto(indexName).source(
              XContentFactory.jsonBuilder
                .rawField(fieldName,
                          JsonFormat.toJsonString(
                            ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(v))
                          ))
                .string()))

    val score: Float = 22.2f // has to be float because that's the search response score type.

    def scriptSearch(i: Int): SearchRequest = {
      search(indexName).query(
        scriptScoreQuery(requests.script.Script(
          s"""
             |boolean b = (boolean) doc[params.field][params.i];
             |double score = params.score;
             |if (b) {
             |  return score;
             |} else {
             |  return 1.0;
             |}
             |""".stripMargin,
          params = Map("score" -> score, "field" -> fieldName, "i" -> i)
        )))
    }

    def check(r: Response[SearchResponse], i: Int): Assertion = {
      val a = r.result.hits.hits.map(_.score).sorted
      val b = boolVectors
        .map(bvec => if (bvec.values(i)) score else 1.0f)
        .sorted
        .toArray
      a shouldBe b
    }

    for {
      _ <- client.execute(deleteIndex(indexName))

      createRes <- client.execute(createIndex(indexName))
      _ = createRes.shouldBeSuccess

      mappingRes <- client.execute(putMapping(Indexes(indexName)).fields(field))
      _ <- mappingRes.shouldBeSuccess

      indexRes <- client.execute(bulk(indexReqs).refresh(RefreshPolicy.IMMEDIATE))
      _ = indexRes.shouldBeSuccess
      _ = indexRes.result.errors shouldBe false

      scriptSearches = boolVectors.indices.map(i => client.execute(scriptSearch(i)))
      scriptResponses <- Future.sequence(scriptSearches)

      _ = forAll(scriptResponses) { _.shouldBeSuccess }
      _ = check(scriptResponses(0), 0)
      _ = check(scriptResponses(1), 1)
      _ = check(scriptResponses(2), 2)

    } yield Succeeded
  }

}
