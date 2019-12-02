package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, IndexedQueryVector}
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn.VectorType.{VECTOR_TYPE_BOOL, VECTOR_TYPE_FLOAT}
import com.klibisz.elastiknn.elastic4s._
import com.klibisz.elastiknn.{Elastic4sMatchers, ElasticAsyncClient, ProcessorOptions, Similarity}
import com.sksamuel.elastic4s.ElasticDsl._
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
class ExactQuerySuite extends AsyncFunSuite with Matchers with Inspectors with Elastic4sMatchers with ElasticAsyncClient {

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
      forAll(testData.corpus) { _.getFloatVector.values should have length 10 }
      forAll(testData.queries) {
        _.vector.getFloatVector.values should have length 10
      }
    }
  }

  for {
    sim <- Similarity.values
    dim <- Seq(10, 128, 512)
  } yield {
    test(s"exact search on $dim-dimensional vectors using $sim") {

      val resourceName = s"${sim.name.toLowerCase}-$dim.json"
      val tryReadData = readTestData(resourceName)
      val vectorType = sim match {
        case SIMILARITY_JACCARD | SIMILARITY_HAMMING => VECTOR_TYPE_BOOL
        case _                                       => VECTOR_TYPE_FLOAT
      }

      val index = s"test-exact-${sim.name.toLowerCase}"
      val pipeline = s"$index-pipeline"
      val rawField = "vec"
      val mapDef = MappingDefinition(Seq(BasicField(rawField, "elastiknn_vector")))

      def corpusId(i: Int): String = s"c$i"
      def queryId(i: Int): String = s"q$i"

      for {

        // Read the test data.
        testData <- Future.fromTry(tryReadData)
        numHits = testData.queries.head.similarities.length

        // Delete the index before running anything.
        _ <- client.execute(deleteIndex(index))

        // Hit setup endpoint.
        setupRes <- client.execute(ElastiKnnSetupRequest())
        _ = setupRes.shouldBeSuccess

        // Create the pipeline.
        popts = ProcessorOptions(rawField, dim, vectorType)
        pipelineReq = PutPipelineRequest(pipeline, s"exact search for ${sim.name}", Processor("elastiknn", popts))
        pipelineRes <- client.execute(pipelineReq)
        _ = pipelineRes.shouldBeSuccess

        // Create the index with mapping.
        createIndexRes <- client.execute(createIndex(index).mapping(mapDef))
        _ = createIndexRes.shouldBeSuccess

        // Index the vectors
        indexVecsReqs = testData.corpus.zipWithIndex.map {
          case (ekv, i) =>
            indexVector(index, popts.fieldRaw, ekv, pipeline).id(corpusId(i))
        }
        indexVecsRes <- client.execute(bulk(indexVecsReqs).refresh(RefreshPolicy.IMMEDIATE))
        _ = indexVecsRes.shouldBeSuccess
        _ = indexVecsRes.result.errors shouldBe false

        // Run exact queries with given vectors.
        givenQueriesAndResponses <- Future.sequence(testData.queries.map { query =>
          val req = search(index)
            .query(knnQuery(ExactQueryOptions(rawField, sim), query.vector))
            .size(numHits)
          client.execute(req).map(res => query -> res)
        })
        _ = givenQueriesAndResponses should have length testData.queries.length
        _ = forAll(givenQueriesAndResponses) {
          case (query, res) =>
            res.shouldBeSuccess
            res.result.hits.hits should have length numHits
            // Just check the similarity scores. Some vectors will have the same scores, so checking indexes is brittle.
            forAll(query.similarities.zip(res.result.hits.hits.map(_.score))) {
              case (sim, score) => score shouldBe sim +- 1e-6f
            }
        }

        // Index the query vectors.
        indexVecsReqs = testData.queries.zipWithIndex.map {
          case (query, i) =>
            indexVector(index, popts.fieldRaw, query.vector, pipeline)
              .id(s"q$i")
        }
        indexVecsRes <- client.execute(bulk(indexVecsReqs).refresh(RefreshPolicy.IMMEDIATE))
        _ = indexVecsRes.shouldBeSuccess
        _ = indexVecsRes.result.errors shouldBe false

        // Run exact queries with indexed vectors. Adjust the size so that it will include at least numHits vectors from
        // the corpus. This lets you filter out other query vectors which might be closer than the corpus vectors.
        indexedQueriesAndResponses <- Future.sequence(testData.queries.zipWithIndex.map {
          case (query, i) =>
            val req = search(index)
              .query(knnQuery(ExactQueryOptions(rawField, sim), IndexedQueryVector(index, rawField, queryId(i))))
              .size(numHits + testData.queries.length + 1)
            client.execute(req).map(res => query -> res)
        })

        _ = indexedQueriesAndResponses should have length testData.queries.length
        _ = forAll(indexedQueriesAndResponses.zipWithIndex) {
          case ((query, res), i) =>
            res.shouldBeSuccess
            val hits = res.result.hits.hits
            hits should have length numHits + testData.queries.length + 1
            // The first hit's id is the same as the query vector's id. It's more reliable to check this by finding
            // the hit matching the id and then making sure it has the max score of all the hits.
            val find = hits.find(_.id == queryId(i))
            find shouldBe defined
            find.map(_.score) shouldBe Some(hits.map(_.score).max)

            // The remaining hits have the right scores. Only consider the corpus vectors.
            val scores = hits.filter(_.id.startsWith("c")).map(_.score).take(query.similarities.length)
            forAll(query.similarities.zip(scores)) {
              case (sim, score) => score shouldBe sim +- 1e-6f
            }
        }

      } yield Succeeded
    }
  }

}
