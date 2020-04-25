package com.klibisz.elastiknn.query

import java.util.UUID

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.{ElasticAsyncClient, Query, SilentMatchers, TestData}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.searches.SearchHit
import org.apache.commons.math3.util.Precision
import org.scalatest.{Assertion, AsyncFunSuite, Inspectors, Matchers}

import scala.concurrent.Future

class NearestNeighborsQuerySuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

  // TODO: find a way to test how recall is affected by different parameter settings.
  // TODO: test vectors in nested fields.

  private val testDataDims = Seq(10, 128, 512)
  private val testDataNumQueries = 30

  private case class Test(mkMappings: Int => Seq[Mapping],
                          mkQuery: (String, Vec) => NearestNeighborsQuery,
                          recall: Double = 1d,
                          scorePrecision: Int = 3)

  private val tests = Seq(
    // Exact
    Test(
      d => Seq(Mapping.SparseBool(d), Mapping.SparseIndexed(d), Mapping.JaccardLsh(d, 10, 1), Mapping.HammingLsh(d, d - 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.Jaccard)
    ),
    Test(
      d => Seq(Mapping.SparseBool(d), Mapping.SparseIndexed(d), Mapping.JaccardLsh(d, 10, 1), Mapping.HammingLsh(d, d - 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.Hamming)
    ),
    Test(
      d => Seq(Mapping.DenseFloat(d), Mapping.AngularLsh(d, 10, 1), Mapping.L2Lsh(d, 10, 1, 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.L1)
    ),
    Test(
      d => Seq(Mapping.DenseFloat(d), Mapping.AngularLsh(d, 10, 1), Mapping.L2Lsh(d, 10, 1, 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.L2)
    ),
    Test(
      d => Seq(Mapping.DenseFloat(d), Mapping.AngularLsh(d, 10, 1), Mapping.L2Lsh(d, 10, 1, 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.Angular)
    ),
    // Sparse indexed
    Test(
      d => Seq(Mapping.SparseIndexed(d)),
      (f, v) => NearestNeighborsQuery.SparseIndexed(f, v, Similarity.Jaccard)
    ),
    Test(
      d => Seq(Mapping.SparseIndexed(d)),
      (f, v) => NearestNeighborsQuery.SparseIndexed(f, v, Similarity.Hamming)
    ),
    // Jaccard Lsh
    Test(
      d => Seq(Mapping.JaccardLsh(d, 20, 1)),
      (f, v) => NearestNeighborsQuery.JaccardLsh(f, v, testDataNumQueries * 2),
      0.8
    ),
    Test(
      d => Seq(Mapping.JaccardLsh(d, 40, 2)),
      (f, v) => NearestNeighborsQuery.JaccardLsh(f, v, testDataNumQueries * 2),
      0.67
    ),
    // Hamming Lsh
    Test(
      d => Seq(Mapping.HammingLsh(d, d / 2)),
      (f, v) => NearestNeighborsQuery.HammingLsh(f, v, testDataNumQueries * 2),
      0.9
    ),
    // Angular Lsh
    Test(
      d => Seq(Mapping.AngularLsh(d, d / 2, 1)),
      (f, v) => NearestNeighborsQuery.AngularLsh(f, v, testDataNumQueries * 3 / 2),
      0.67
    ),
    // L2 Lsh
    Test(
      d => Seq(Mapping.L2Lsh(d, d * 2 / 3, 1, 3)),
      (f, v) => NearestNeighborsQuery.L2Lsh(f, v, testDataNumQueries * 3 / 2),
      0.67
    )
  )

  private def corpusId(i: Int): String = s"c$i"
  private def queryId(i: Int): String = s"q$i"

  for {
    Test(mkMappings, mkQuery, recall, scorePrecision) <- tests
    dims <- testDataDims
    mapping <- mkMappings(dims)
  } {
    val fieldName = "vec"
    val fakeQuery = mkQuery(fieldName, Vec.Indexed("", "", ""))
    val testData = TestData.read(fakeQuery.similarity, dims).get
    val indexName = s"test-index-${UUID.randomUUID.toString}"
    val testName = f"$indexName%-30s ${fakeQuery.similarity}%-16s $mapping%-30s ${fakeQuery.withVec(testData.queries.head.vector)}%-60s"

    test(testName) {

      for {
        // Setup the index.
        _ <- eknn.execute(createIndex(indexName))
        _ <- eknn.putMapping(indexName, fieldName, mapping)

        // Read and index the test data corpus.
        corpusIds = testData.corpus.indices.map(corpusId)
        _ <- eknn.index(indexName, fieldName, testData.corpus, Some(corpusIds), RefreshPolicy.IMMEDIATE)

        // Search using literal vectors.
        kLiteral = testData.queries.head.similarities.length
        literalKnnReqs = testData.queries.map { q =>
          eknn.nearestNeighbors(indexName, fakeQuery.withVec(q.vector), kLiteral)
        }
        literalKnnRes <- Future.sequence(literalKnnReqs)

        // Index the query vectors.
        queryIds = testData.queries.indices.map(queryId)
        _ <- eknn.index(indexName, fieldName, testData.queries.map(_.vector), Some(queryIds), RefreshPolicy.IMMEDIATE)

        // Search using the indexed query vectors.
        // Increase k to account for the fact that there are queryIds.length new vectors in the corpus.
        kIndexed = kLiteral + queryIds.length
        indexedKnnReqs = queryIds.map { id =>
          eknn.nearestNeighbors(indexName, fakeQuery.withVec(Vec.Indexed(indexName, id, fieldName)), kIndexed)
        }
        indexedKnnRes <- Future.sequence(indexedKnnReqs)

      } yield {

        def checkHitsVsQuery(hits: Array[SearchHit], query: Query): Assertion = {

          hits should have length kLiteral

          // Round the scores for easier comparison.
          val hitScores = hits.map(_.score).map(Precision.round(_, scorePrecision))
          val correctScores = query.similarities.map(Precision.round(_, scorePrecision))

          // You can only really compare scores, not ids, since multiple vectors can have the same score.
          // There should be at least recallCount matching scores
          val recallCount: Int = (query.similarities.length * recall).toInt
          var hitScoresRemaining = hitScores.toVector
          forAtLeast(recallCount, correctScores) { s =>
            val a = hitScoresRemaining should contain(s)
            val i = hitScoresRemaining.indexOf(s)
            hitScoresRemaining = hitScoresRemaining.patch(i, Nil, 1)
            a
          }
        }

        // Test the literal query results.
        forAll(testData.queries.zip(literalKnnRes).silent) {
          case (query, res) => checkHitsVsQuery(res.result.hits.hits, query)
        }

        // Test the indexed query results.
        forAll(testData.queries.zip(indexedKnnRes).silent) {
          case (query, res) =>
            val hits = res.result.hits.hits.filter(h => corpusIds.contains(h.id)).take(kLiteral)
            checkHitsVsQuery(hits, query)
        }
      }
    }
  }

}
