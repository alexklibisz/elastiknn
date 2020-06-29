package com.klibisz.elastiknn.query

import java.util.UUID

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.{ElasticAsyncClient, Query, SilentMatchers, TestData}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Response
import com.sksamuel.elastic4s.requests.searches.SearchResponse
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
  private val shards: Int = 2
  private val segmentsPerShard: Int = 1
  private val recallTolerance: Double = 1e-2
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
        NearestNeighborsQuery.Exact(fieldName, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.Hamming) -> 1d,
        NearestNeighborsQuery.JaccardLsh(fieldName, 400) -> 0.73,
        NearestNeighborsQuery.JaccardLsh(fieldName, 800) -> 0.89
      )
    ),
    Test(
      Mapping.JaccardLsh(dims, 300, 2),
      Seq(
        NearestNeighborsQuery.JaccardLsh(fieldName, 400) -> 0.72,
        NearestNeighborsQuery.JaccardLsh(fieldName, 800) -> 0.86
      )
    ),
    // Hamming LSH
    Test(
      Mapping.HammingLsh(dims, dims * 5 / 10),
      Seq(
        NearestNeighborsQuery.Exact(fieldName, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.Hamming) -> 1d,
        NearestNeighborsQuery.HammingLsh(fieldName, 200) -> 0.71,
        NearestNeighborsQuery.HammingLsh(fieldName, 400) -> 0.86
      )
    ),
    Test(
      Mapping.HammingLsh(dims, dims * 7 / 10),
      Seq(
        NearestNeighborsQuery.HammingLsh(fieldName, 200) -> 0.89,
        NearestNeighborsQuery.HammingLsh(fieldName, 400) -> 0.97
      )
    ),
    // Angular Lsh
    Test(
      Mapping.AngularLsh(dims, 400, 1),
      Seq(
        NearestNeighborsQuery.Exact(fieldName, Similarity.L1) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.L2) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.Angular) -> 1d,
        NearestNeighborsQuery.AngularLsh(fieldName, 400) -> 0.48,
        NearestNeighborsQuery.AngularLsh(fieldName, 800) -> 0.69
      )
    ),
    Test(
      Mapping.AngularLsh(dims, 400, 2),
      Seq(
        NearestNeighborsQuery.AngularLsh(fieldName, 200) -> 0.36,
        NearestNeighborsQuery.AngularLsh(fieldName, 400) -> 0.52,
        NearestNeighborsQuery.AngularLsh(fieldName, 800) -> 0.74
      )
    ),
    // L2 Lsh
    Test(
      Mapping.L2Lsh(dims, 600, 1, 4),
      Seq(
        NearestNeighborsQuery.Exact(fieldName, Similarity.L1) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.L2) -> 1d,
        NearestNeighborsQuery.Exact(fieldName, Similarity.Angular) -> 1d,
        NearestNeighborsQuery.L2Lsh(fieldName, 200) -> 0.13,
        NearestNeighborsQuery.L2Lsh(fieldName, 400) -> 0.24,
        NearestNeighborsQuery.L2Lsh(fieldName, 800) -> 0.44
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
          _ <- Future.traverse(testData.corpus.zipWithIndex.grouped(500)) { batch =>
            val (vecs, ids) = (batch.map(_._1), batch.map(x => s"v${x._2}"))
            eknn.index(corpusIndex, fieldName, vecs, Some(ids))
          }
          _ <- Future.traverse(testData.queries.zipWithIndex.grouped(500)) { batch =>
            val (vecs, ids) = (batch.map(_._1.vector), batch.map(x => s"v${x._2}"))
            eknn.index(queriesIndex, fieldName, vecs, Some(ids))
          }
          _ <- eknn.execute(refreshIndex(corpusIndex, queriesIndex))
          _ <- eknn.execute(forceMerge(corpusIndex, queriesIndex).maxSegments(segmentsPerShard))
          // TODO: is the last refresh necessary?
          _ <- eknn.execute(refreshIndex(corpusIndex, queriesIndex))
        } yield ()
    } yield ()

  private def recall(queries: Vector[Query], resultsIx: Int, responses: Seq[Response[SearchResponse]]): Double = {
    val numMatches = queries
      .zip(responses)
      .map {
        case (Query(_, correctResults), response) =>
          val correctScores: Vector[Float] = correctResults(resultsIx).values.map(_.toFloat)
          val hitScores: Array[Float] = response.result.hits.hits.map(_.score)
          correctScores.intersect(hitScores).length
      }
      .sum
    numMatches * 1d / responses.map(_.result.hits.hits.length).sum
  }

  for {
    Test(mapping, queriesAndExpectedRecall) <- tests.take(3)
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
    val testName = f"${uuid}%-20s ${mapping.toString}%-30s ${query.toString}%-50s ~= ${expectedRecall}%-8f"
    // Lookup the correct results based on the similarity function.
    val resultsIx = testData.queries.head.results.zipWithIndex.filter(_._1.similarity == query.similarity).head._2
    test(testName) {
      for {
        _ <- index(corpusIndex, queriesIndex, mapping, testData)
        explicitResponses1 <- Future.sequence(testData.queries.map { q =>
          eknn.nearestNeighbors(corpusIndex, query.withVec(q.vector), k, preference = Some(""))
        })
        explicitResponses2 <- Future.sequence(testData.queries.map { q =>
          eknn.nearestNeighbors(corpusIndex, query.withVec(q.vector), k, preference = Some(""))
        })
        explicitResponses3 <- Future.sequence(testData.queries.map { q =>
          eknn.nearestNeighbors(corpusIndex, query.withVec(q.vector), k, preference = Some(""))
        })
        indexedResponses <- Future.sequence(testData.queries.zipWithIndex.map {
          case (q, i) =>
            val vec = Vec.Indexed(queriesIndex, s"v$i", fieldName)
            eknn.nearestNeighbors(corpusIndex, query.withVec(vec), k, preference = Some(""))
        })
      } yield {

        // First compute recall.
        val explicitRecall1 = recall(testData.queries, resultsIx, explicitResponses1)
        val explicitRecall2 = recall(testData.queries, resultsIx, explicitResponses2)
        val explicitRecall3 = recall(testData.queries, resultsIx, explicitResponses3)
        val indexedRecall = recall(testData.queries, resultsIx, indexedResponses)

        // Make sure results were deterministic.
        withClue(s"Explicit query recalls should be deterministic") {
          explicitRecall2 shouldBe explicitRecall1
          explicitRecall3 shouldBe explicitRecall1
        }

        // Make sure recall is at or above expected.
        withClue(s"Explicit query recall should be ${expectedRecall} +/- ${recallTolerance}") {
          explicitRecall1 shouldBe expectedRecall +- (recallTolerance)
        }

        withClue(s"Indexed query recall should be ${expectedRecall} +/- ${recallTolerance}") {
          indexedRecall shouldBe expectedRecall +- (recallTolerance)
        }
      }
    }
  }

}
