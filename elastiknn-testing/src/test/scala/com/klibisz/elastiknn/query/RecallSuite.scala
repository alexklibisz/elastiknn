package com.klibisz.elastiknn.query

import java.util.UUID
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.{ElasticAsyncClient, Query, TestData}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Response
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import futil.Futil
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.util.hashing.MurmurHash3.orderedHash

/**
  * Tests for recall regressions for all of the mappings and their queries using random vectors.
  * There are some subtleties:
  * - Recall is evaluated based on the scores returned, not the ids, to account for cases where multiple vectors could
  *   have the same score relative a query vector.
  * - Using more shards will generally increase recall for LSH queries because candidates are evaluated per _segment_.
  *   Each shard can have a non-specific number of segments but we merge each shard to a specific number.
  * - Repeated query results against the same index should be deterministic. However if you re-index the data and run
  *   the same query, I have seen different results at times. This seems to be an effect at the Elasticsearch level.
  *   I've tested at the Lucene (sans ES) level and that seems to be reliably deterministic.
  */
class RecallSuite extends AsyncFunSuite with Matchers with ElasticAsyncClient {

  // Each test case consists of setting up one Mapping and then running several queries against that mapping.
  // Each query has an expected recall that will be checked.
  private case class Test(mapping: Mapping, queriesAndExpectedRecall: Seq[(NearestNeighborsQuery, Double)], recallTolerance: Double = 1e-2)

  private val numCores = Runtime.getRuntime.availableProcessors()
  private val vecField: String = "vec"
  private val storedIdField: String = "id"
  private val dims: Int = 1024
  private val k: Int = 100
  private val shards: Int = 2
  private val segmentsPerShard: Int = 1
  private val sparseBoolTestData = TestData.read("testdata-sparsebool.json.gz")
  private val denseFloatTestData = TestData.read("testdata-densefloat.json.gz")
  private val denseFloatUnitTestData = TestData.read("testdata-densefloat-unit.json.gz")

  private val tests = Seq(
    // Exact
    Test(
      Mapping.SparseBool(dims),
      Seq(
        NearestNeighborsQuery.Exact(vecField, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.Hamming) -> 1d
      )
    ),
    Test(
      Mapping.DenseFloat(dims),
      Seq(
        NearestNeighborsQuery.Exact(vecField, Similarity.L1) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.L2) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.Cosine) -> 1d
      )
    ),
    // SparseIndexed
    Test(
      Mapping.SparseIndexed(dims),
      Seq(
        NearestNeighborsQuery.Exact(vecField, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.Hamming) -> 1d,
        NearestNeighborsQuery.SparseIndexed(vecField, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.SparseIndexed(vecField, Similarity.Hamming) -> 1d
      )
    ),
    // Jaccard LSH
    Test(
      Mapping.JaccardLsh(dims, 200, 1),
      Seq(
        NearestNeighborsQuery.Exact(vecField, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.Hamming) -> 1d,
        NearestNeighborsQuery.JaccardLsh(vecField, 400) -> 0.69,
        NearestNeighborsQuery.JaccardLsh(vecField, 800) -> 0.87
      )
    ),
    Test(
      Mapping.JaccardLsh(dims, 300, 2),
      Seq(
        NearestNeighborsQuery.JaccardLsh(vecField, 400) -> 0.62,
        NearestNeighborsQuery.JaccardLsh(vecField, 800) -> 0.81
      )
    ),
    // Hamming LSH
    Test(
      Mapping.HammingLsh(dims, dims * 1 / 2, 1),
      Seq(
        NearestNeighborsQuery.Exact(vecField, Similarity.Jaccard) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.Hamming) -> 1d,
        NearestNeighborsQuery.HammingLsh(vecField, 200) -> 0.72,
        NearestNeighborsQuery.HammingLsh(vecField, 400) -> 0.92
      )
    ),
    Test(
      // Increasing k increases recall up to a point.
      Mapping.HammingLsh(dims, dims * 2 / 5, 2),
      Seq(NearestNeighborsQuery.HammingLsh(vecField, 200) -> 0.86)
    ),
    Test(
      // But increasing it too far decreases recall.
      Mapping.HammingLsh(dims, dims * 2 / 5, 4),
      Seq(NearestNeighborsQuery.HammingLsh(vecField, 200) -> 0.65)
    ),
    // Cosine Lsh
    Test(
      Mapping.CosineLsh(dims, 400, 1),
      Seq(
        NearestNeighborsQuery.Exact(vecField, Similarity.L1) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.L2) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.Cosine) -> 1d,
        NearestNeighborsQuery.CosineLsh(vecField, 400) -> 0.46,
        NearestNeighborsQuery.CosineLsh(vecField, 800) -> 0.67
      )
    ),
    Test(
      Mapping.CosineLsh(dims, 400, 2),
      Seq(
        NearestNeighborsQuery.CosineLsh(vecField, 200) -> 0.34,
        NearestNeighborsQuery.CosineLsh(vecField, 400) -> 0.50,
        NearestNeighborsQuery.CosineLsh(vecField, 800) -> 0.72
      )
    ),
    // L2 Lsh
    Test(
      Mapping.L2Lsh(dims, 600, 1, 4),
      Seq(
        NearestNeighborsQuery.Exact(vecField, Similarity.L1) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.L2) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.Cosine) -> 1d,
        NearestNeighborsQuery.L2Lsh(vecField, 200) -> 0.12,
        NearestNeighborsQuery.L2Lsh(vecField, 400) -> 0.22,
        NearestNeighborsQuery.L2Lsh(vecField, 800) -> 0.40,
        // Adding probes should improve recall, but since k = 1, probing > 2 times should have no effect.
        NearestNeighborsQuery.L2Lsh(vecField, 800, 1) -> 0.43,
        NearestNeighborsQuery.L2Lsh(vecField, 800, 2) -> 0.49,
        NearestNeighborsQuery.L2Lsh(vecField, 800, 10) -> 0.49
      )
    ),
    // Permutation Lsh
    Test(
      Mapping.PermutationLsh(dims, 128, true),
      Seq(
        NearestNeighborsQuery.Exact(vecField, Similarity.L1) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.L2) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.Cosine) -> 1d,
        NearestNeighborsQuery.PermutationLsh(vecField, Similarity.Cosine, 200) -> 0.14,
        NearestNeighborsQuery.PermutationLsh(vecField, Similarity.Cosine, 400) -> 0.21,
        NearestNeighborsQuery.PermutationLsh(vecField, Similarity.L2, 200) -> 0.12,
        NearestNeighborsQuery.PermutationLsh(vecField, Similarity.L2, 400) -> 0.20
      ),
      // TODO: This one seems to be more sensitive for some unknown reason.
      recallTolerance = 5e-2
    ),
    Test(
      Mapping.PermutationLsh(dims, 128, false),
      Seq(
        NearestNeighborsQuery.Exact(vecField, Similarity.L1) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.L2) -> 1d,
        NearestNeighborsQuery.Exact(vecField, Similarity.Cosine) -> 1d,
        NearestNeighborsQuery.PermutationLsh(vecField, Similarity.Cosine, 200) -> 0.31,
        NearestNeighborsQuery.PermutationLsh(vecField, Similarity.Cosine, 400) -> 0.51,
        NearestNeighborsQuery.PermutationLsh(vecField, Similarity.L2, 200) -> 0.3,
        NearestNeighborsQuery.PermutationLsh(vecField, Similarity.L2, 400) -> 0.43
      ),
      // TODO: This one seems to be more sensitive for some unknown reason.
      recallTolerance = 5e-2
    )
  )

  private def index(corpusIndex: String, queriesIndex: String, mapping: Mapping, testData: TestData): Future[Unit] =
    for {
      corpusExists <- eknn.execute(indexExists(corpusIndex)).map(_.result.exists).recoverWith {
        case _ => Future.successful(false)
      }
      queryExists <- eknn.execute(indexExists(queriesIndex)).map(_.result.exists).recoverWith {
        case _ => Future.successful(false)
      }
      _ <- if (corpusExists && queryExists) Future.successful(())
      else
        for {
          _ <- eknn.createIndex(corpusIndex, shards)
          _ <- eknn.putMapping(corpusIndex, vecField, storedIdField, mapping)
          _ <- eknn.createIndex(queriesIndex)
          _ <- eknn.putMapping(queriesIndex, vecField, storedIdField, mapping)
          _ <- Futil.traverseSerial(testData.corpus.zipWithIndex.grouped(100)) { batch =>
            val (vecs, ids) = (batch.map(_._1), batch.map(x => s"v${x._2}"))
            eknn.index(corpusIndex, vecField, vecs, storedIdField, ids)
          }
          _ <- Futil.traverseSerial(testData.queries.zipWithIndex.grouped(100)) { batch =>
            val (vecs, ids) = (batch.map(_._1.vector), batch.map(x => s"v${x._2}"))
            eknn.index(queriesIndex, vecField, vecs, storedIdField, ids)
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
          val minCorrectScore = correctResults(resultsIx).values.min
          val numGreaterEqual = response.result.hits.hits.count(_.score >= minCorrectScore)
          numGreaterEqual
      }
      .sum
    numMatches * 1d / queries.map(_.results(resultsIx).values.length).sum
  }

  for {
    Test(mapping, queriesAndExpectedRecall, recallTolerance) <- tests
    (query, expectedRecall) <- queriesAndExpectedRecall
    testData = query.similarity match {
      case Similarity.Jaccard => sparseBoolTestData
      case Similarity.Hamming => sparseBoolTestData
      case Similarity.L1      => denseFloatTestData
      case Similarity.L2      => denseFloatTestData
      case Similarity.Cosine  => denseFloatUnitTestData
    }
  } {
    val uuid = UUID.randomUUID().toString
    val corpusIndex = f"test-data-$uuid-c"
    val queriesIndex = f"test-data-$uuid-q"
    val testName = f"$uuid%-20s ${mapping.toString}%-30s ${query.toString}%-50s ~= ${expectedRecall}%-8f"
    // Lookup the correct results based on the similarity function.
    val resultsIx = testData.queries.head.results.zipWithIndex.filter(_._1.similarity == query.similarity).head._2
    test(testName) {
      for {
        _ <- index(corpusIndex, queriesIndex, mapping, testData)
        explicitResponses1 <- Futil.traverseParN(numCores)(testData.queries) { q =>
          eknn.nearestNeighbors(corpusIndex, query.withVec(q.vector), k, storedIdField)
        }
        explicitResponses2 <- Futil.traverseParN(numCores)(testData.queries) { q =>
          eknn.nearestNeighbors(corpusIndex, query.withVec(q.vector), k, storedIdField)
        }
        explicitResponses3 <- Futil.traverseParN(numCores)(testData.queries) { q =>
          eknn.nearestNeighbors(corpusIndex, query.withVec(q.vector), k, storedIdField)
        }
        indexedResponses <- Futil.traverseParN(numCores)(testData.queries.zipWithIndex) {
          case (_, i) =>
            val vec = Vec.Indexed(queriesIndex, s"v$i", vecField)
            eknn.nearestNeighbors(corpusIndex, query.withVec(vec), k, storedIdField)
        }
        _ <- eknn.execute(deleteIndex(corpusIndex))
        _ <- eknn.execute(deleteIndex(queriesIndex))
      } yield {

        // First compute recall.
        val explicitRecall1 = recall(testData.queries, resultsIx, explicitResponses1)
        val explicitRecall2 = recall(testData.queries, resultsIx, explicitResponses2)
        val explicitRecall3 = recall(testData.queries, resultsIx, explicitResponses3)
        val indexedRecall = recall(testData.queries, resultsIx, indexedResponses)

        // Print the hashcodes for the returned ids and scores. These should all be identical.
        val idsHashCodes = Seq(explicitResponses1, explicitResponses2, explicitResponses3, indexedResponses).map { responses =>
          orderedHash(responses.flatMap(_.result.hits.hits.map(_.id)))
        }
        val scoresHashCodes = Seq(explicitResponses1, explicitResponses2, explicitResponses3, indexedResponses).map { responses =>
          orderedHash(responses.flatMap(_.result.hits.hits.map(_.score)))
        }
        info(s"IDs hashes: ${idsHashCodes.mkString(",")}")
        info(s"Scores hashes: ${scoresHashCodes.mkString(",")}")

        // Make sure results were deterministic.
        withClue(s"Explicit query recalls should be deterministic") {
          explicitRecall2 shouldBe explicitRecall1
          explicitRecall3 shouldBe explicitRecall1
        }

        // Make sure recall is at or above expected.
        withClue(s"Explicit query recall") {
          explicitRecall1 shouldBe expectedRecall +- recallTolerance
        }

        withClue(s"Indexed query recall") {
          indexedRecall shouldBe expectedRecall +- recallTolerance
        }
      }
    }
  }

}
