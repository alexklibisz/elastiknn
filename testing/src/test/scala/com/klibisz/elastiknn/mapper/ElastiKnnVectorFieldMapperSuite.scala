package com.klibisz.elastiknn.mapper

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.CreateIndexResponse
import com.sksamuel.elastic4s.requests.mappings.BasicField
import com.sksamuel.elastic4s.{Indexes, Response, requests}
import io.circe.parser._
import com.klibisz.elastiknn.utils.Utils._
import com.klibisz.elastiknn.client.ElastiKnnDsl._
import com.klibisz.elastiknn.{ElasticAsyncClient, SparseBoolVector, _}
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

  def index(ekvs: Seq[ElastiKnnVector], indexName: String): Future[Unit] =
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
    } yield ()

  def testIndexRetrieve(ekvs: Seq[ElastiKnnVector], indexName: String): Future[Assertion] =
    for {
      _ <- index(ekvs, indexName)

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

  test("index and retrieve float vectors") {
    val ekvs = FloatVector.randoms(10, 9).map(ElastiKnnVector(_))
    testIndexRetrieve(ekvs, "test-index-retrieve-float-vectors")
  }

  test("index and retrieve sparse bool vectors") {
    val ekvs = SparseBoolVector.randoms(10, 9).map(ElastiKnnVector(_))
    testIndexRetrieve(ekvs, "test-index-retrieve-bool-vectors")
  }

  test("index float vectors and use script to sum them") {
    val indexName = "test-index-script-sum-float-vectors"
    val ekvs = FloatVector.randoms(10, 9).map(ElastiKnnVector(_))
    for {
      _ <- index(ekvs, indexName)
      searchReq = search(indexName).query(
        scriptScoreQuery(requests.script.Script(
          """
          |def vec = doc[params.field];
          |double sum = 0.0;
          |for (n in vec) sum += n;
          |sum += vec.size();
          |return sum;
          |""".stripMargin,
          params = Map("field" -> fieldName)
        )))
      searchRes <- client.execute(searchReq)
      _ = searchRes.shouldBeSuccess
      scores = searchRes.result.hits.hits.map(_.score).sorted
      correct = ekvs.flatMap(_.vector.floatVector).map(v => v.values.sum.toFloat + v.values.length).sorted
      _ = scores should have length correct.length
    } yield
      forAll(scores.zip(correct)) {
        case (a, b) => a shouldBe (b +- 1e-5f)
      }
  }

  test("index boolean vectors and use script to sum them") {
    val indexName = "test-index-script-sum-bool-vectors"
    val ekvs = SparseBoolVector.randoms(10, 9).map(ElastiKnnVector(_))
    for {
      _ <- index(ekvs, indexName)
      searchReq = search(indexName).query(
        scriptScoreQuery(requests.script.Script(
          """
            |def vec = doc[params.field];
            |int sum = 0;
            |for (i in vec) sum += i;
            |sum += vec.size();  // The number of true indices.
            |sum += vec.get(-1); // The total number of indices.
            |return sum * 1.0;
            |""".stripMargin,
          params = Map("field" -> fieldName)
        )))
      searchRes <- client.execute(searchReq)
      _ = searchRes.shouldBeSuccess
      scores = searchRes.result.hits.hits.map(_.score).sorted
      correct = ekvs
        .flatMap(_.vector.sparseBoolVector)
        .map(v => v.trueIndices.sum + v.trueIndices.length + v.totalIndices)
        .sorted
      _ = scores should have length correct.length
    } yield
      forAll(scores.zip(correct)) {
        case (a, b) => a shouldBe b.toInt
      }
  }

}
