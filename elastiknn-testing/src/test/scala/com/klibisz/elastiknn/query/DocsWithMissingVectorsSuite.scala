package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.client.ElastiknnRequests
import com.klibisz.elastiknn.testing.{ElasticAsyncClient, SilentMatchers}
import com.sksamuel.elastic4s.ElasticDsl._
import futil.Futil
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inspectors, _}

import scala.util.Random

class DocsWithMissingVectorsSuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

  // Based on two issues:
  // https://github.com/alexklibisz/elastiknn/issues/181
  // https://github.com/alexklibisz/elastiknn/issues/180
  // This seemed to only cause runtime issues with >= 20k documents.
  // The solution was to ensure the DocIdSetIterator in the MatchHashesAndScoreQuery begins iterating at docID = -1.
  test("search docs with a vector mapping, but only half the indexed docs have vectors") {
    implicit val rng = new Random(0)
    val index = "issue-181"
    val (vecField, idField, dims, numDocs) = ("vec", "id", 128, 20000)
    val vecMapping: Mapping = Mapping.CosineLsh(dims, 99, 1)
    val mappingJsonString =
      s"""
         |{
         |  "properties": {
         |    "$vecField": ${XContentCodec.encodeUnsafeToString(vecMapping)},
         |    "$idField": { "type": "keyword" }
         |  }
         |}
         |""".stripMargin

    val v0 = Vec.DenseFloat.random(dims)

    for {
      _ <- deleteIfExists(index)
      _ <- eknn.createIndex(index)
      _ <- eknn.execute(putMapping(index).rawSource(mappingJsonString))
      _ <- Futil.traverseSerial((0 until numDocs).grouped(500)) { ids =>
        val reqs = ids.map { i =>
          if (i % 2 == 0) ElastiknnRequests.index(index, vecField, if (i == 0) v0 else Vec.DenseFloat.random(dims), idField, s"v$i")
          else indexInto(index).id(s"v$i").fields(Map(idField -> s"v$i"))
        }
        eknn.execute(bulk(reqs))
      }
      _ <- eknn.execute(refreshIndex(index))

      countWithIdField <- eknn.execute(count(index).query(existsQuery(idField)))
      countWithVecField <- eknn.execute(count(index).query(existsQuery(vecField)))
      _ = countWithIdField.result.count shouldBe numDocs
      _ = countWithVecField.result.count shouldBe numDocs / 2

      nbrsExact <- eknn.nearestNeighbors(index, NearestNeighborsQuery.Exact(vecField, Similarity.Cosine, v0), 10, idField)
      _ = nbrsExact.result.hits.hits.length shouldBe 10
      _ = nbrsExact.result.hits.hits.head.score shouldBe 2f

      nbrsApprox <- eknn.nearestNeighbors(index, NearestNeighborsQuery.CosineLsh(vecField, 13, v0), 10, idField)
      _ = nbrsApprox.result.hits.hits.length shouldBe 10
      _ = nbrsApprox.result.hits.hits.head.score shouldBe 2f
    } yield Assertions.succeed
  }

}
