package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.ElasticAsyncClient
import com.sksamuel.elastic4s.ElasticDsl._
import futil.Futil
import org.scalatest.Inspectors
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random
import scala.util.hashing.MurmurHash3

class FunctionScoreQuerySuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient {

  implicit val rng: Random = new Random(0)
  val indexPrefix = "issue-97b"

  // Generate a corpus of docs. Small number of the them have color blue, rest have color green.
  val dims = 10
  val numTotal = 1000
  val numBlue = 100
  val corpus: Seq[(String, Vec.DenseFloat, String)] =
    (0 until numTotal).map(i => (s"v$i", Vec.DenseFloat.random(dims), if (i < numBlue) "blue" else "green"))

  // Make sure there are fewer candidates than the whole corpus. This ensures the filter is executed before the knn.
  val candidates = numBlue

  // Test with multiple mappings/queries.
  val queryVec = Vec.DenseFloat.random(dims)
  val mappingsAndQueries = Seq(
    Mapping.L2Lsh(dims, 40, 1, 2) -> Seq(
      NearestNeighborsQuery.Exact("vec", Similarity.L2, queryVec),
      NearestNeighborsQuery.Exact("vec", Similarity.Angular, queryVec),
      NearestNeighborsQuery.L2Lsh("vec", candidates, vec = queryVec)
    ),
    Mapping.AngularLsh(dims, 40, 1) -> Seq(
      NearestNeighborsQuery.Exact("vec", Similarity.L2, queryVec),
      NearestNeighborsQuery.Exact("vec", Similarity.Angular, queryVec),
      NearestNeighborsQuery.AngularLsh("vec", candidates, queryVec)
    )
  )

  for {
    (mapping, queries) <- mappingsAndQueries
    query: NearestNeighborsQuery <- queries
    index = s"$indexPrefix-${MurmurHash3.orderedHash(Seq(mapping, query), 0).toString}"
  } test(s"filters with mapping [${mapping}] and query [${query}] in index [$index]") {
    val rawMapping =
      s"""
         |{
         |  "properties": {
         |    "vec": ${ElasticsearchCodec.mapping(mapping).noSpaces},
         |    "color": { "type": "keyword" }
         |  }
         |}
         |""".stripMargin

    val termQuery =
      s"""
         |{
         |  "size": $numBlue,
         |  "query": { "term": { "color": "blue" } }
         |}
         |""".stripMargin

    def functionScoreQueryReplace(q: NearestNeighborsQuery) =
      s"""
         |{
         |  "size": $numBlue,
         |  "query": {
         |    "function_score": {
         |      "query": { "term": { "color": "blue" } },
         |      "boost_mode": "replace",
         |      "elastiknn_nearest_neighbors": ${ElasticsearchCodec.nospaces(q)}
         |    }
         |  }
         |}
         |""".stripMargin

    val functionScoreQuerySumWeight3 =
      s"""
         |{
         |  "size": $numBlue,
         |  "query": {
         |    "function_score": {
         |      "query": { "term": { "color": "blue" } },
         |      "boost_mode": "sum",
         |      "functions": [
         |        {
         |          "elastiknn_nearest_neighbors": ${ElasticsearchCodec.nospaces(query)},
         |          "weight": 3
         |        }
         |      ]
         |    }
         |  }
         |}
         |""".stripMargin

    for {
      _ <- deleteIfExists(index)
      _ <- eknn.createIndex(index)
      _ <- eknn.execute(putMapping(index).rawSource(rawMapping))
      _ <- Futil.traverseSerial(corpus.grouped(100)) { batch =>
        val reqs = batch.map {
          case (id, vec, color) =>
            val docSource = s"""{ "vec": ${ElasticsearchCodec.nospaces(vec)}, "color": "$color" }"""
            indexInto(index).id(id).source(docSource)
        }
        eknn.execute(bulk(reqs))
      }
      _ <- eknn.execute(refreshIndex(index))
      // Simple term query that just returns all docs with "color": "blue".
      termRes <- eknn.execute(search(index).source(termQuery)).map(_.result)

      // Function score query that runs KNN on docs matching "color": "blue" and returns the KNN score.
      fsReplaceRes <- eknn.execute(search(index).source(functionScoreQueryReplace(query))).map(_.result)

      // The function score query does not work with indexed vectors. Cause an error to verify this.
      indexedException <- recoverToExceptionIf[Exception](
        eknn.execute(search(index).source(functionScoreQueryReplace(query.withVec(Vec.Indexed(index, "v0", "vec")))))
      )

      // Function score query that runs KNN on docs matching "color": "blue" and returns the terms score + 3 * KNN score.
      // The previous two queries are used to check the results of this query.
      fsSumWeightedRes <- eknn.execute(search(index).source(functionScoreQuerySumWeight3)).map(_.result)
    } yield {
      val blueIds = corpus.filter(_._3 == "blue").map(_._1).toSet

      termRes.hits.hits.length shouldBe numBlue
      termRes.hits.hits.map(_.id).toSet shouldBe blueIds

      fsReplaceRes.hits.hits.length shouldBe numBlue
      fsReplaceRes.hits.hits.map(_.id).toSet shouldBe blueIds

      indexedException.getMessage should include("The score function does not support indexed vectors.")

      fsSumWeightedRes.hits.hits.length shouldBe numBlue
      fsSumWeightedRes.hits.hits.map(_.id).toSet shouldBe blueIds

      val termScoreById = termRes.hits.hits.map(h => (h.id -> h.score)).toMap
      val fsReplaceScoreById = fsReplaceRes.hits.hits.map(h => (h.id -> h.score)).toMap
      forAll(fsSumWeightedRes.hits.hits.toVector) { h =>
        val expected: Double = (termScoreById(h.id) + 3 * fsReplaceScoreById(h.id))
        h.score.toDouble shouldBe expected +- 0.001
      }
    }
  }

}
