package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.TestData
import com.klibisz.elastiknn.{ElasticAsyncClient, SilentMatchers}
import com.sksamuel.elastic4s.ElasticDsl._
import com.oblac.nomen.Nomen
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import org.apache.commons.math3.util.Precision
import org.scalatest.{AsyncFunSuite, Inspectors, Matchers, Succeeded}

import scala.concurrent.Future

class NearestNeighborsQuerySuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

  private case class Test(similarity: Similarity,
                          mkMappings: Int => Seq[Mapping],
                          mkQuery: (String, Vec) => NearestNeighborsQuery,
                          recall: Double = 1d,
                          scorePrecision: Int = 3)

  private val tests = Seq(
    Test(
      Similarity.Jaccard,
      (dims: Int) => Seq(Mapping.SparseBool(dims), Mapping.SparseIndexed(dims), Mapping.JaccardLsh(dims, 10, 1)),
      (field: String, vec: Vec) => NearestNeighborsQuery.Exact(field, vec, Similarity.Jaccard)
    ),
    Test(
      Similarity.Hamming,
      (dims: Int) => Seq(Mapping.SparseBool(dims), Mapping.SparseIndexed(dims), Mapping.JaccardLsh(dims, 10, 1)),
      (field: String, vec: Vec) => NearestNeighborsQuery.Exact(field, vec, Similarity.Hamming)
    ),
    Test(
      Similarity.L1,
      (dims: Int) => Seq(Mapping.DenseFloat(dims)),
      (field: String, vec: Vec) => NearestNeighborsQuery.Exact(field, vec, Similarity.L1)
    ),
    Test(
      Similarity.L2,
      (dims: Int) => Seq(Mapping.DenseFloat(dims)),
      (field: String, vec: Vec) => NearestNeighborsQuery.Exact(field, vec, Similarity.L2)
    ),
    Test(
      Similarity.Angular,
      (dims: Int) => Seq(Mapping.DenseFloat(dims)),
      (field: String, vec: Vec) => NearestNeighborsQuery.Exact(field, vec, Similarity.Angular)
    )
  )

  private def corpusId(i: Int): String = s"c$i"
  private def queryId(i: Int): String = s"q$i"

  for {
    Test(similarity, mkMappings, mkQuery, recall, scorePrecision) <- tests
    dims <- Seq(10, 128, 512)
    mapping <- mkMappings(dims)
  } {
    val fieldName = "vec"
    val testData = TestData.read(similarity, dims).get
    val initQuery = mkQuery(fieldName, testData.queries.head.vector)
    val indexName = Nomen.randomName()
    val testName = f"$indexName%-30s $similarity%-16s $mapping%-30s $initQuery%-60s"

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
          eknn.nearestNeighbors(indexName, initQuery.withVector(q.vector), kLiteral, fetchSource = false)
        }
        literalKnnRes <- Future.sequence(literalKnnReqs)

        // Index the query vectors.
        queryIds = testData.queries.indices.map(queryId)
        _ <- eknn.index(indexName, fieldName, testData.queries.map(_.vector), Some(queryIds), RefreshPolicy.IMMEDIATE)

        // Search using the indexed query vectors.
        kIndexed = kLiteral + queryIds.length + 1

      } yield {
        forAll(testData.queries.zip(literalKnnRes).silent) {
          case (query, res) =>
            val hits = res.result.hits.hits
            hits should have length kLiteral

            // Create tuples of ids and rounded scores.
            val hitIdsAndScores = hits.map(h => h.id -> Precision.round(h.score, scorePrecision))
            val correctIdsAndScores = query.indices.map(corpusId).zip(query.similarities.map(Precision.round(_, scorePrecision)))

            // The smallest hit score should equal the smallest correct score.
            val minHitScore = hitIdsAndScores.map(_._2).min
            val minCorrectScore = correctIdsAndScores.map(_._2).min
            minHitScore shouldBe minCorrectScore

            // Each of the correct (id, score) tuples should be returned as a hit, unless it's equal to the min score,
            // since that means it could have been excluded as an edge case.
            forAtLeast((query.similarities.length * recall).toInt, correctIdsAndScores) {
              case (id, score) =>
                if (score == minCorrectScore) Succeeded
                else hitIdsAndScores should contain((id, score))
            }
        }
      }
    }
  }

}
