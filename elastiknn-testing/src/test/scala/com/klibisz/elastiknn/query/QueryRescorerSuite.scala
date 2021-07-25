package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.ElasticAsyncClient
import com.sksamuel.elastic4s.ElasticDsl._
import futil.Futil
import org.scalatest._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random
import scala.util.hashing.MurmurHash3

class QueryRescorerSuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient {

  // https://github.com/alexklibisz/elastiknn/issues/97
  implicit val rng: Random = new Random(0)
  val indexPrefix = "issue-97"

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
      NearestNeighborsQuery.Exact("vec", Similarity.Cosine, queryVec),
      NearestNeighborsQuery.L2Lsh("vec", candidates, vec = queryVec)
    ),
    Mapping.CosineLsh(dims, 40, 1) -> Seq(
      NearestNeighborsQuery.Exact("vec", Similarity.L2, queryVec),
      NearestNeighborsQuery.Exact("vec", Similarity.Cosine, queryVec),
      NearestNeighborsQuery.CosineLsh("vec", candidates, queryVec)
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
         |    "color": {
         |      "type": "keyword"
         |    }
         |  }
         |}
         |""".stripMargin
    val rawQuery =
      s"""
         |{
         |  "size": $numBlue,
         |  "query": {
         |    "term": { "color": "blue" }
         |  },
         |  "rescore": {
         |    "window_size": $candidates,
         |    "query": {
         |      "rescore_query": {
         |        "elastiknn_nearest_neighbors": ${ElasticsearchCodec.nospaces(query)}
         |      },
         |      "query_weight": 0,
         |      "rescore_query_weight": 1
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
      res <- eknn.execute(search(index).source(rawQuery))
    } yield {
      res.result.hits.hits.length shouldBe numBlue
      res.result.hits.hits.map(_.id).toSet shouldBe corpus.filter(_._3 == "blue").map(_._1).toSet
    }
  }

}
