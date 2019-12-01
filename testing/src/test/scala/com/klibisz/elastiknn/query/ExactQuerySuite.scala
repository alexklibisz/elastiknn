package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn.KNearestNeighborsQuery.ExactQueryOptions
import com.klibisz.elastiknn.VectorType.{VECTOR_TYPE_BOOL, VECTOR_TYPE_DOUBLE}
import com.klibisz.elastiknn.elastic4s._
import com.klibisz.elastiknn.{
  Elastic4sMatchers,
  ElasticAsyncClient,
  ProcessorOptions,
  Similarity
}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.mappings.{BasicField, MappingDefinition}
import io.circe.parser.decode
import org.scalatest._

import scala.concurrent.Future
import scala.io.BufferedSource
import scala.util.Try

class ExactQuerySuite
    extends AsyncFunSuite
    with Matchers
    with Inspectors
    with Elastic4sMatchers
    with ElasticAsyncClient {

  private def readTestData(resourceName: String): Try[TestData] = {
    val src: BufferedSource = scala.io.Source.fromResource(resourceName)
    val rawJson = try src.mkString
    finally src.close()
    decode[TestData](rawJson).toTry
  }

  test("parses test data") {
    for {
      testData <- Future.fromTry(readTestData("similarity_angular-10.json"))
    } yield {
      forAll(testData.corpus) { _.getDoubleVector.values should have length 10 }
      forAll(testData.queries) {
        _.vector.getDoubleVector.values should have length 10
      }
    }
  }

  val filter: Set[Similarity] =
    Set(SIMILARITY_L1, SIMILARITY_L2, SIMILARITY_ANGULAR, SIMILARITY_JACCARD)

  for {
    sim <- Similarity.values.filter(filter.contains)
    dim <- Seq(10, 128, 512)
  } yield {
    test(s"exact search on $dim-dimensional vectors with $sim distance") {

      val resourceName = s"${sim.name.toLowerCase}-$dim.json"
      val tryReadData = readTestData(resourceName)
      val vectorType = sim match {
        case SIMILARITY_JACCARD | SIMILARITY_HAMMING => VECTOR_TYPE_BOOL
        case _                                       => VECTOR_TYPE_DOUBLE
      }

      val index = s"test-exact-${sim.name.toLowerCase}"
      val pipeline = s"$index-pipeline"
      val rawField = "vec"
      val mapDef =
        MappingDefinition(Seq(BasicField(rawField, "elastiknn_vector")))

      for {

        // Read the test data.
        testData <- Future.fromTry(tryReadData)

        // Delete the index before running anything.
        _ <- client.execute(deleteIndex(index))

        // Hit setup endpoint.
        setupRes <- client.execute(ElastiKnnSetupRequest())
        _ = setupRes.shouldBeSuccess

        // Create the pipeline.
        popts = ProcessorOptions(rawField, dim, vectorType)
        pipelineReq = PutPipelineRequest(pipeline,
                                         s"exact search for ${sim.name}",
                                         Processor("elastiknn", popts))
        pipelineRes <- client.execute(pipelineReq)
        _ = pipelineRes.shouldBeSuccess

        // Create the index with mapping.
        createIndexRes <- client.execute(createIndex(index).mapping(mapDef))
        _ = createIndexRes.shouldBeSuccess

        // Index the vectors
        indexVecsReqs = testData.corpus.zipWithIndex.map {
          case (ekv, i) =>
            indexVector(index, popts.fieldRaw, ekv, pipeline).id(i.toString)
        }
        indexVecsRes <- client.execute(
          bulk(indexVecsReqs).refresh(RefreshPolicy.IMMEDIATE))
        _ = indexVecsRes.shouldBeSuccess
        _ = indexVecsRes.result.errors shouldBe false

        // Run exact query.
        queriesAndResponses <- Future.sequence(testData.queries.map { query =>
          val req = search(index).query(
            knnQuery(ExactQueryOptions(rawField, sim), query.vector))
          client.execute(req).map(res => query -> res)
        })
        _ = queriesAndResponses should have length testData.queries.length
        _ = forAll(queriesAndResponses) {
          case (query, res) =>
            res.shouldBeSuccess
            res.result.hits.hits should have length query.indices.length
            // Just check the similarity scores. Some vectors will have the same scores, so checking indexes is brittle.
            forAll(query.similarities.zip(res.result.hits.hits)) {
              case (sim, hit) => hit.score shouldBe sim +- 1e-6.toFloat
            }
        }

      } yield Succeeded
    }
  }

}
