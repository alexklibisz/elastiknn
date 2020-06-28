package com.klibisz.elastiknn.query

import java.util.UUID

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.{ElasticAsyncClient, Query, SilentMatchers, TestData}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import org.scalatest.{AsyncFunSuite, Inspectors, Matchers}

import scala.concurrent.Future

/**
  * Tests for recall regressions for all of the mappings and their queries using random vectors.
  * There are some subtleties:
  * - Recall is evaluated based on the scores returned, not the ids, to account for cases where multiple vectors could
  *   have the same score relative a query vector.
  * - Using more shards will generally increase recall for LSH queries because it's evaluating more candidates.
  * - You can get different scores for the same query across multiple runs. Setting the preference string should make
  *   scores more consistent. It seems to be sufficient to use a random UUID that's unique to the specific run.
  */
class NearestNeighborsQueryRecallSuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

  // Each test case consists of setting up one Mapping and then running several queries against that mapping.
  // Each query has an expected recall that will be checked.
  private case class Test(mapping: Mapping, queriesAndExpectedRecall: Seq[(NearestNeighborsQuery, Double)])

  private val fieldName: String = "vec"
  private val dims: Int = 1024
  private val k: Int = 100
  private val shards: Int = 1
  private val segmentsPerShard: Int = 1
  private val sparseBoolTestData = TestData.read("testdata-sparsebool.json.gz")
  private val denseFloatTestData = TestData.read("testdata-densefloat.json.gz")
  private val denseFloatUnitTestData = TestData.read("testdata-densefloat-unit.json.gz")

  private val tests = Seq(
    // Exact
    Test(
      Mapping.SparseBool(dims),
      Seq(
        NearestNeighborsQuery.Exact(fieldName, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.Hamming) -> 1d
      )
    ),
    Test(
      Mapping.DenseFloat(dims),
      Seq(
        NearestNeighborsQuery.Exact(fieldName, Similarity.L1) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.L2) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.Angular) -> 1d
      )
    ),
    // SparseIndexed
    Test(
      Mapping.SparseIndexed(dims),
      Seq(
        NearestNeighborsQuery.Exact(fieldName, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.Hamming) -> 1d,
        NearestNeighborsQuery.SparseIndexed(fieldName, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.SparseIndexed(fieldName, Similarity.Hamming) -> 1d
      )
    ),
    // Jaccard LSH
    Test(
      Mapping.JaccardLsh(dims, 200, 1),
      Seq(
//        NearestNeighborsQuery.Exact(fieldName, Similarity.Jaccard) -> 1d,
//        NearestNeighborsQuery.Exact(fieldName, Similarity.Hamming) -> 1d,
        NearestNeighborsQuery.JaccardLsh(fieldName, 400) -> 1.0,
//        NearestNeighborsQuery.JaccardLsh(fieldName, 800) -> 0.9
      )
    ),
    Test(
      Mapping.JaccardLsh(dims, 300, 2),
      Seq(
        NearestNeighborsQuery.JaccardLsh(fieldName, 300) -> 0.6,
        NearestNeighborsQuery.JaccardLsh(fieldName, 400) -> 0.75,
        NearestNeighborsQuery.JaccardLsh(fieldName, 800) -> 0.85
      )
    ),
    // Hamming LSH
    Test(
      Mapping.HammingLsh(dims, dims * 7 / 10),
      Seq(
        NearestNeighborsQuery.Exact(fieldName, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.Hamming) -> 1d,
        NearestNeighborsQuery.HammingLsh(fieldName, 500) -> 0.5,
        NearestNeighborsQuery.HammingLsh(fieldName, 800) -> 0.9
      )
    ),
    Test(
      Mapping.HammingLsh(dims, dims * 9 / 10),
      Seq(
        NearestNeighborsQuery.HammingLsh(fieldName, 300) -> 1d,
        // NearestNeighborsQuery.HammingLsh(fieldName, 400) -> 1d
      )
    ),
    // Angular Lsh
    Test(
      Mapping.AngularLsh(dims, 400, 1),
      Seq(
//        NearestNeighborsQuery.Exact(fieldName, Similarity.L1) -> 1d,
//        NearestNeighborsQuery.Exact(fieldName, Similarity.L2) -> 1d,
//        NearestNeighborsQuery.Exact(fieldName, Similarity.Angular) -> 1d,
        NearestNeighborsQuery.AngularLsh(fieldName, 200) -> 0.5,
//        NearestNeighborsQuery.AngularLsh(fieldName, 400) -> 0.65,
//        NearestNeighborsQuery.AngularLsh(fieldName, 800) -> 0.85
      )
    ),
//    Test(
//      Mapping.AngularLsh(dims, 400, 2),
//      Seq(
//        NearestNeighborsQuery.AngularLsh(fieldName, 200) -> 0.5,
//        NearestNeighborsQuery.AngularLsh(fieldName, 400) -> 0.65,
//        NearestNeighborsQuery.AngularLsh(fieldName, 800) -> 0.85
//      )
//    ),
    // L2 Lsh
    Test(
      Mapping.L2Lsh(dims, 400, 1, 3),
      Seq(
//        NearestNeighborsQuery.Exact(fieldName, Similarity.L1) -> 1d,
//        NearestNeighborsQuery.Exact(fieldName, Similarity.L2) -> 1d,
//        NearestNeighborsQuery.Exact(fieldName, Similarity.Angular) -> 1d,
        NearestNeighborsQuery.L2Lsh(fieldName, 200) -> 0.27,
        NearestNeighborsQuery.L2Lsh(fieldName, 400) -> 0.44,
        NearestNeighborsQuery.L2Lsh(fieldName, 800) -> 0.67
      )
    )
  )

  private def index(corpusIndex: String, queriesIndex: String, mapping: Mapping, testData: TestData): Future[Unit] =
    for {
      corpusExists <- client.execute(indexExists(corpusIndex)).map(_.result.exists)
      queryExists <- client.execute(indexExists(queriesIndex)).map(_.result.exists)
      _ <- if (corpusExists && queryExists) Future.successful(())
      else
        for {
          _ <- eknn.execute(createIndex(corpusIndex).shards(shards).replicas(0))
          _ <- eknn.putMapping(corpusIndex, fieldName, mapping)
          _ <- eknn.execute(createIndex(queriesIndex).shards(shards).replicas(0))
          _ <- eknn.putMapping(queriesIndex, fieldName, mapping)
          _ <- Future.traverse(testData.corpus.grouped(500)) { batch =>
            eknn.index(corpusIndex, fieldName, batch, Some(batch.map(x => s"v${x.hashCode()}")))
          }
          _ <- Future.traverse(testData.queries.grouped(500)) { batch =>
            eknn.index(queriesIndex, fieldName, batch.map(_.vector), Some(batch.map(x => s"v${x.vector.hashCode()}")))
          }
          _ <- eknn.execute(forceMerge(corpusIndex, queriesIndex).maxSegments(segmentsPerShard))
          _ <- eknn.execute(refreshIndex(corpusIndex, queriesIndex))
          // _ <- Future(Thread.sleep(1000))
        } yield ()
    } yield ()

  for {
    Test(mapping, queriesAndExpectedRecall) <- tests.drop(3).take(1)
    (query, expectedRecall) <- queriesAndExpectedRecall
    testData = query.similarity match {
      case Similarity.Jaccard => sparseBoolTestData
      case Similarity.Hamming => sparseBoolTestData
      case Similarity.L1      => denseFloatTestData
      case Similarity.L2      => denseFloatTestData
      case Similarity.Angular => denseFloatUnitTestData
    }
  } {
    val uuid = UUID.randomUUID().toString
    val corpusIndex = f"test-data-$uuid-c"
    val queriesIndex = f"test-data-$uuid-q"
    val testName = f"${uuid}%-20s ${mapping.toString}%-30s ${query.toString}%-50s >= ${expectedRecall}%-8f"
    // Lookup the correct results based on the similarity function.
    val resultsIx = testData.queries.head.results.zipWithIndex.filter(_._1.similarity == query.similarity).head._2
    test(testName) {
      for {
        _ <- index(corpusIndex, queriesIndex, mapping, testData)
        givenQueryResponses <- Future.traverse(testData.queries) { q =>
          eknn.nearestNeighbors(corpusIndex, query.withVec(q.vector), k, preference = Some("foo"))
        }
//        indexedQueryResponses <- Future.traverse(testData.queries) { q =>
//          val vec = Vec.Indexed(queriesIndex, s"v${q.vector.hashCode()}", fieldName)
//          eknn.nearestNeighbors(corpusIndex, query.withVec(vec), k, preference = Some("foo"))
//        }
      } yield {

        val givenQueriesRecall = testData.queries
          .zip(givenQueryResponses)
          .map {
            case (Query(_, correctResults), response) =>
              val correctScores = correctResults(resultsIx).values.map(_.toFloat)
              val hitScores = response.result.hits.hits.map(_.score)
              correctScores.intersect(hitScores).length * 1d
          }
          .sum / (givenQueryResponses.length * k)

        givenQueriesRecall shouldBe >=(expectedRecall)

//        val indexedQueriesRecall = testData.queries
//          .zip(indexedQueryResponses)
//          .map {
//            case (Query(_, correctResults), response) =>
//              val correctScores = correctResults(resultsIx).values.map(_.toFloat)
//              val hitScores = response.result.hits.hits.map(_.score)
//              correctScores.intersect(hitScores).length * 1d
//          }
//          .sum / (testData.queries.length * k)
//
//        indexedQueriesRecall shouldBe >=(expectedRecall)
      }
    }
  }

}
