package org.elasticsearch.elastiknn.mapper

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.{CreateIndexResponse, IndexRequest}
import com.sksamuel.elastic4s.requests.mappings.BasicField
import com.sksamuel.elastic4s.requests.searches.{SearchRequest, SearchResponse}
import com.sksamuel.elastic4s.{Indexes, Response, XContentFactory, requests}
import io.circe.parser._
import org.elasticsearch.elastiknn.client.ElastiKnnDsl._
import org.elasticsearch.elastiknn.utils.Implicits._
import org.elasticsearch.elastiknn.{ElasticAsyncClient, SparseBoolVector, _}
import org.scalatest._
import scalapb_circe.JsonFormat

import scala.concurrent.Future
import scala.util.{Random, Try}

class ElastiKnnVectorFieldMapperSuite
    extends AsyncFunSuite
    with Matchers
    with Inspectors
    with SilentMatchers
    with Elastic4sMatchers
    with ElasticAsyncClient {

  private val fieldName = "ekv"
  private val field = BasicField(fieldName, "elastiknn_vector")

  implicit val rng: Random = new Random(0)

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

  test("index and retrieve float vectors") {

    val indexName = "test-index-retrieve-float-vectors"
    val ekvs: Seq[ElastiKnnVector] = FloatVector.randoms(10, 5).map(ElastiKnnVector(_))

    for {
      _ <- client.execute(deleteIndex(indexName))

      createRes <- client.execute(createIndex(indexName))
      _ = createRes.shouldBeSuccess

      mappingRes <- client.execute(putMapping(Indexes(indexName)).fields(elastiKnnVectorField(fieldName)))
      _ = mappingRes.shouldBeSuccess

      indexReqs = ekvs.map(v => indexVector(indexName, fieldName, v))
      indexRes <- client.execute(bulk(indexReqs).refresh(RefreshPolicy.IMMEDIATE))
      _ = indexRes.shouldBeSuccess
      _ = indexRes.result.errors shouldBe false

      getRes <- client.execute(search(indexName).query(matchAllQuery()))
      _ = getRes.shouldBeSuccess

      ekvsFromSource <- Future.fromTry(
        getRes.result.hits.hits
          .sortBy(_.id)
          .toVector
          .map(h =>
            for {
              parsed <- parse(h.sourceAsString).toTry
              json <- Try(parsed.findAllByKey(fieldName).head)
            } yield JsonFormat.fromJson[ElastiKnnVector](json))
          .sequence)

    } yield {
      ekvsFromSource should have length ekvs.length
      forAll(ekvs) { v1 =>
        ekvsFromSource.find(v2 => ElastiKnnVector.equal(v1, v2)) shouldBe defined
      }
    }

  }

  test("index and script search float vectors") {
    val indexName = "test-index-script-search-double-vectors"

    val floatVectors = Seq(
      FloatVector(Array(0.99, 0.12, -0.34)),
      FloatVector(Array(0.22, 0.19, 0.44)),
      FloatVector(Array(0.33, -0.119, 0.454))
    )

    val offset = 3

    val indexReqs: Seq[IndexRequest] =
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

    for {
      _ <- client.execute(deleteIndex(indexName))

      createRes <- client.execute(createIndex(indexName))
      _ = createRes.shouldBeSuccess

      mappingRes <- client.execute(putMapping(Indexes(indexName)).fields(field))
      _ <- mappingRes.shouldBeSuccess

      indexRes <- client.execute(bulk(indexReqs).refresh(RefreshPolicy.IMMEDIATE))
      _ = indexRes.shouldBeSuccess
      _ = indexRes.result.errors shouldBe false

      query = scriptScoreQuery(
        requests.script.Script(
          s"""
           |def a = doc[params.field];
           |double n = 0.0;
           |for (i in a) n += 1;
           |return n;
           |""".stripMargin,
          params = Map("field" -> fieldName)
        ))
      searchRes <- client.execute(search(indexName).query(query))
      _ = searchRes.shouldBeSuccess
      scores = searchRes.result.hits.hits.map(_.score).sorted
      correctScores = boolVectors.map(_.trueIndices.length).sorted.toArray
      _ = scores shouldBe correctScores
    } yield Succeeded
  }

}
