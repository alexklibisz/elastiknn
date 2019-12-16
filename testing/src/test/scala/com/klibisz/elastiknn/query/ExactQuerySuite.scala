package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, IndexedQueryVector, QueryOptions}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.client.ElastiKnnClient
import com.klibisz.elastiknn.client.ElastiKnnDsl._
import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.mappings.{BasicField, MappingDefinition}
import io.circe.parser.decode
import org.scalatest._

import scala.concurrent.Future
import scala.io.BufferedSource
import scala.util.Try

/**
  * Tests for the exact query functionality, using test data generated via Python and scikit-learn.
  */
class ExactQuerySuite
    extends AsyncFunSuite
    with QuerySuite
    with Matchers
    with SilentMatchers
    with Inspectors
    with Elastic4sMatchers
    with ElasticAsyncClient
    with ElasticDsl {

  private val eknn: ElastiKnnClient = new ElastiKnnClient()

  private def readTestData(resourceName: String): Try[TestData] =
    for {
      rawJson <- Try {
        val src: BufferedSource = scala.io.Source.fromResource(resourceName)
        try src.mkString
        finally src.close()
      }
      decoded <- decode[TestData](rawJson).toTry
    } yield decoded

  test("parses test data") {
    for {
      testData <- Future.fromTry(readTestData("similarity_angular-10.json"))
    } yield {
      forAll(testData.corpus.silent) { _.getFloatVector.values should have length 10 }
      forAll(testData.queries.silent) {
        _.vector.getFloatVector.values should have length 10
      }
    }
  }

  for {
    sim <- Similarity.values
    dim <- Seq(10, 128, 512)
  } yield {
    val support = new Support("vec_raw", sim, dim, ModelOptions.Exact(ExactModelOptions(sim)))
    test(s"exact search on $dim-dimensional vectors using ${sim.name}") {
      support.testGiven(QueryOptions.Exact(ExactQueryOptions("vec_raw", sim))) { queriesAndResults =>
        forAll(queriesAndResults.silent) {
          case (query, res) =>
            res.hits.hits should have length query.similarities.length
            // Just check the similarity scores. Some vectors will have the same scores, so checking indexes is brittle.
            forAll(query.similarities.zip(res.hits.hits.map(_.score)).silent) {
              case (sim, score) => score shouldBe sim +- 1e-6f
            }
        }
      }
    }
  }

//  for {
//    sim <- Similarity.values
//    dim <- Seq(10, 128, 512)
//  } yield {
//    test(s"exact search on $dim-dimensional vectors using $sim") {
//
//      val resourceName = s"${sim.name.toLowerCase}-$dim.json"
//      val tryReadData = readTestData(resourceName)
//
//      val index = s"test-exact-${sim.name.toLowerCase}"
//      val pipelineId = s"$index-pipeline"
//      val rawField = "vec"
//      val mapDef = MappingDefinition(Seq(BasicField(rawField, "elastiknn_vector")))
//
//      def corpusId(i: Int): String = s"c$i"
//      def queryId(i: Int): String = s"q$i"
//
//      for {
//
//        // Read the test data.
//        testData <- Future.fromTry(tryReadData)
//        numHits = testData.queries.head.similarities.length
//
//        // Delete the index before running anything.
//        _ <- client.execute(deleteIndex(index))
//
//        // Hit setup endpoint.
//        _ <- eknn.setupCluster()
//
//        // Create the pipeline.
//        popts = ProcessorOptions(rawField, dim, modelOptions = ModelOptions.Exact(ExactModelOptions(sim)))
//        _ <- eknn.createPipeline(pipelineId, popts)
//
//        // Create the index with mapping.
//        createIndexRes <- client.execute(createIndex(index).mapping(mapDef))
//        _ = createIndexRes.shouldBeSuccess
//
//        // Index the vectors
//        corpusIds = testData.corpus.indices.map(corpusId)
//        _ <- eknn.indexVectors(index, pipelineId, popts.fieldRaw, testData.corpus, Some(corpusIds), RefreshPolicy.Immediate)
//
//        // Run exact queries with given vectors.
//        givenQueriesAndResponses <- Future.sequence(testData.queries.map { query =>
//          eknn.knnQuery(index, ExactQueryOptions(rawField, sim), query.vector, numHits).map(res => query -> res)
//        })
//        _ = givenQueriesAndResponses should have length testData.queries.length
//        _ = forAll(givenQueriesAndResponses.silent) {
//          case (query, res) =>
//            res.hits.hits should have length numHits
//            // Just check the similarity scores. Some vectors will have the same scores, so checking indexes is brittle.
//            forAll(query.similarities.zip(res.hits.hits.map(_.score)).silent) {
//              case (sim, score) => score shouldBe sim +- 1e-6f
//            }
//        }
//
//        // Index the query vectors.
//        _ <- eknn.indexVectors(index,
//                               pipelineId,
//                               popts.fieldRaw,
//                               testData.queries.map(_.vector),
//                               ids = Some(testData.queries.indices.map(queryId)),
//                               RefreshPolicy.Immediate)
//
//        // Run exact queries with indexed vectors. Adjust the size so that it will include at least numHits vectors from
//        // the corpus. This lets you filter out other query vectors which might be closer than the corpus vectors.
//        indexedQueriesAndResponses <- Future.sequence(testData.queries.zipWithIndex.map {
//          case (query, i) =>
//            eknn
//              .knnQuery(index,
//                        ExactQueryOptions(rawField, sim),
//                        IndexedQueryVector(index, rawField, queryId(i)),
//                        numHits + testData.queries.length + 1)
//              .map(res => query -> res)
//        })
//
//        _ = indexedQueriesAndResponses should have length testData.queries.length
//        _ = forAll(indexedQueriesAndResponses.zipWithIndex.silent) {
//          case ((query, res), i) =>
//            val hits = res.hits.hits
//            hits should have length numHits + testData.queries.length + 1
//            // The first hit's id is the same as the query vector's id. It's more reliable to check this by finding
//            // the hit matching the id and then making sure it has the max score of all the hits.
//            val find = hits.find(_.id == queryId(i))
//            find shouldBe defined
//            find.map(_.score) shouldBe Some(hits.map(_.score).max)
//
//            // The remaining hits have the right scores. Only consider the corpus vectors.
//            val scores = hits.filterNot(_.id.startsWith("q")).map(_.score).take(query.similarities.length)
//            forAll(query.similarities.zip(scores).silent) {
//              case (sim, score) => score shouldBe sim +- 1e-6f
//            }
//        }
//
//      } yield Succeeded
//    }
//  }

}
