package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.TestData
import com.klibisz.elastiknn.{ElasticAsyncClient, SilentMatchers}
import com.sksamuel.elastic4s.ElasticDsl._
import com.oblac.nomen.Nomen
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import org.scalatest.{AsyncFunSuite, Inspectors, Matchers, Succeeded}

import scala.concurrent.Future

class NearestNeighborsQuerySuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

  private case class Test(similarity: Similarity,
                          mkMappings: Int => Seq[Mapping],
                          mkQuery: (String, Vec) => NearestNeighborsQuery,
                          recall: Double = 1d)

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
    Test(similarity, mkMappings, mkQuery, recall) <- tests
    dims <- Seq(10, 128, 512)
    mapping <- mkMappings(dims)
  } {
    val fieldName = "vec"
    val initQuery = mkQuery(fieldName, Vec.Indexed("index", "id", fieldName))
    val indexName = Nomen.randomName()
    val testName = f"$indexName%-20s $similarity%-16s $mapping%-30s $initQuery%-50s"

    test(testName) {

      for {
        // Setup the index.
        _ <- eknn.execute(createIndex(indexName))
        _ <- eknn.putMapping(indexName, fieldName, mapping)

        // Read and index the test data corpus.
        testData <- Future.fromTry(TestData.read(similarity, dims))
        corpusIds = testData.corpus.indices.map(corpusId)
        _ <- eknn.index(indexName, fieldName, testData.corpus, Some(corpusIds), RefreshPolicy.IMMEDIATE)

        // Search using literal vectors.
        k = testData.queries.head.similarities.length
        literalKnnReqs = testData.queries.map { q =>
          eknn.nearestNeighbors(indexName, initQuery.withVector(q.vector), k, fetchSource = false)
        }
        literalKnnRes <- Future.sequence(literalKnnReqs)

        // Index the query vectors.
        // ...
        // Search using the indexed query vectors.

      } yield {
//        println(testData)
//        println(indexName)
//        println(literalKnnRes)
        Succeeded
      }
    }
  }

}
