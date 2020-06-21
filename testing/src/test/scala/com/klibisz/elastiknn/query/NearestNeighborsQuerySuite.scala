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

/**
  * Tests all of the mappings and queries against random vectors.
  * This is more a regression testing suite than a pure test suite.
  */
class NearestNeighborsQuerySuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

  private def corpusId(i: Int): String = s"c$i"
  private def queryId(i: Int): String = s"q$i"

  private case class Test(mkMappings: Int => Seq[Mapping],
                          mkQuery: (String, Vec) => NearestNeighborsQuery,
                          dimsToMinRecall: Map[Int, Double])

  private val fieldName = "vec"

  private val tests = Seq(
    // Exact
    Test(
      d => Seq(Mapping.SparseBool(d), Mapping.SparseIndexed(d), Mapping.JaccardLsh(d, 10, 1), Mapping.HammingLsh(d, d - 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.Jaccard),
      Map(10 -> 1d, 128 -> 1d, 1024 -> 1d)
    ),
    Test(
      d => Seq(Mapping.SparseBool(d), Mapping.SparseIndexed(d), Mapping.JaccardLsh(d, 10, 1), Mapping.HammingLsh(d, d - 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.Hamming),
      Map(10 -> 1d, 128 -> 1d, 1024 -> 1d)
    ),
    Test(
      d => Seq(Mapping.DenseFloat(d), Mapping.AngularLsh(d, 10, 1), Mapping.L2Lsh(d, 10, 1, 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.L1),
      Map(10 -> 1d, 128 -> 1d, 1024 -> 1d)
    ),
    Test(
      d => Seq(Mapping.DenseFloat(d), Mapping.AngularLsh(d, 10, 1), Mapping.L2Lsh(d, 10, 1, 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.L2),
      Map(10 -> 1d, 128 -> 1d, 1024 -> 1d)
    ),
    Test(
      d => Seq(Mapping.DenseFloat(d), Mapping.AngularLsh(d, 10, 1), Mapping.L2Lsh(d, 10, 1, 1)),
      (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.Angular),
      Map(10 -> 1d, 128 -> 1d, 1024 -> 1d)
    ),
//    // Sparse indexed
//    Test(
//      d => Seq(Mapping.SparseIndexed(d)),
//      (f, v) => NearestNeighborsQuery.SparseIndexed(f, v, Similarity.Jaccard)
//    ),
//    Test(
//      d => Seq(Mapping.SparseIndexed(d)),
//      (f, v) => NearestNeighborsQuery.SparseIndexed(f, v, Similarity.Hamming)
//    ),
//    // Jaccard Lsh
//    Test(
//      d => Seq(Mapping.JaccardLsh(d, 20, 1)),
//      (f, v) => NearestNeighborsQuery.JaccardLsh(f, testDataNumQueries * 2, v),
//      0.8
//    ),
//    Test(
//      d => Seq(Mapping.JaccardLsh(d, 40, 2)),
//      (f, v) => NearestNeighborsQuery.JaccardLsh(f, testDataNumQueries * 2, v),
//      0.67
//    ),
//    // Hamming Lsh
//    Test(
//      d => Seq(Mapping.HammingLsh(d, d / 2)),
//      (f, v) => NearestNeighborsQuery.HammingLsh(f, testDataNumQueries * 2, v),
//      0.9
//    ),
//    // Angular Lsh
//    Test(
//      d => Seq(Mapping.AngularLsh(d, d / 2, 1)),
//      (f, v) => NearestNeighborsQuery.AngularLsh(f, testDataNumQueries * 3 / 2, v),
//      0.67
//    ),
//    // L2 Lsh
//    Test(
//      d => Seq(Mapping.L2Lsh(d, d * 2 / 3, 1, 3)),
//      (f, v) => NearestNeighborsQuery.L2Lsh(f, testDataNumQueries * 3 / 2, v),
//      0.67
//    )
  )

  for {
    Test(mkMappings, mkQuery, dimsToMinRecall) <- tests
    (dims, minRecall) <- dimsToMinRecall
    query = mkQuery(fieldName, Vec.Empty())
    testData = TestData.read(query.similarity, dims).get
    mapping <- mkMappings(dims)
  } {

    val query = mkQuery(fieldName, Vec.Empty())
    val indexName = s"test-index-${UUID.randomUUID.toString}"
    val testName = f"$indexName%-30s ${query.similarity}%-16s $mapping%-30s ${query.withVec(testData.queries.head.vector)}%-60s"

    test(testName) {

      for {
        // Setup the index.
        _ <- eknn.execute(createIndex(indexName).shards(2).replicas(0))
        _ <- eknn.putMapping(indexName, fieldName, mapping)

        // Read and index the test data corpus.
        corpusIds = testData.corpus.indices.map(corpusId)
        _ <- Future.traverse(testData.corpus.zip(corpusIds).grouped(500)) { b =>
          eknn.index(indexName, fieldName, b.map(_._1), Some(b.map(_._2)))
        }
        _ <- eknn.execute(refreshIndex(indexName))

        // Search using literal vectors.
        kLiteral = testData.queries.head.similarities.length
        literalKnnReqs = testData.queries.map { q =>
          eknn.nearestNeighbors(indexName, query.withVec(q.vector), kLiteral)
        }
        literalKnnRes <- Future.sequence(literalKnnReqs)

        // Index the query vectors.
        queryIds = testData.queries.indices.map(queryId)
        _ <- Future.traverse(testData.queries.map(_.vector).zip(queryIds).grouped(500)) { b =>
          eknn.index(indexName, fieldName, b.map(_._1), Some(b.map(_._2)))
        }
        _ <- eknn.execute(refreshIndex(indexName))

        // Search using the indexed query vectors.
        // Increase k to account for the fact that there are queryIds.length new vectors in the corpus.
        kIndexed = kLiteral + queryIds.length
        indexedKnnReqs = queryIds.map { id =>
          eknn.nearestNeighbors(indexName, query.withVec(Vec.Indexed(indexName, id, fieldName)), kIndexed)
        }
        indexedKnnRes <- Future.sequence(indexedKnnReqs)

      } yield {

        // You can only really compare scores, not ids, since multiple vectors can have the same score.
        def checkHitsVsQuery(hits: Array[SearchHit], query: Query): Assertion = {
          hits should have length kLiteral
          val hitScores = hits.map(_.score).map(Precision.round(_, 4))
          val correctScores = query.similarities.map(Precision.round(_, 4))
          val recall = hitScores.intersect(correctScores).length * 1d / hitScores.length
          recall shouldBe >=(minRecall)
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
