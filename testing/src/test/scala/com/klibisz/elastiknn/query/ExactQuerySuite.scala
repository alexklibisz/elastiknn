package com.klibisz.elastiknn.query

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStream}

import com.klibisz.elastiknn.Distance._
import com.klibisz.elastiknn.KNearestNeighborsQuery.ExactQueryOptions
import com.klibisz.elastiknn.VectorType.{VECTOR_TYPE_BOOL, VECTOR_TYPE_DOUBLE}
import com.klibisz.elastiknn.elastic4s._
import com.klibisz.elastiknn.{Distance, ElasticAsyncClient, KNearestNeighborsQuery, ProcessorOptions}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import io.circe.parser.decode
import org.elasticsearch.common.io.stream.{InputStreamStreamInput, OutputStreamStreamOutput}
import org.scalatest._

import scala.concurrent.Future
import scala.io.BufferedSource
import scala.util.Try


class ExactQuerySuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient {

  private def readTestData(resourceName: String): Try[TestData] = {
    val src: BufferedSource = scala.io.Source.fromResource(resourceName)
    val rawJson = try src.mkString finally src.close()
    decode[TestData](rawJson).toTry
  }

  test("parses test data") {
    for {
      testData <- Future.fromTry(readTestData("distance_angular-10.json"))
    } yield {
      forAll(testData.corpus) { _.getDoubleVector.values should have length 10 }
      forAll(testData.queries) { _.vector.getDoubleVector.values should have length 10 }
    }
  }

  for {
    dist <- Distance.values.filter(_ == DISTANCE_ANGULAR)
    dim <- Seq(10, 128, 512)
  } yield {
    test(s"exact search on $dim-dimensional vectors with $dist distance") {

      val resourceName = s"${dist.name.toLowerCase}-$dim.json"
      val tryReadData = readTestData(resourceName)
      val vectorType = dist match {
        case DISTANCE_JACCARD | DISTANCE_HAMMING => VECTOR_TYPE_BOOL
        case _ => VECTOR_TYPE_DOUBLE
      }


      val index = s"test-exact-${dist.name.toLowerCase}"
      val pipeline = s"$index-pipeline"
      val rawField = "vecRaw"

      for {

        // Read the test data.
        testData <- Future.fromTry(tryReadData)

        // Delete the index before running anything.
        _ <- client.execute(deleteIndex(index))

        // Hit setup endpoint.
        setupRes <- client.execute(ElastiKnnSetupRequest())
        _ = setupRes.isSuccess shouldBe true

        // Create the pipeline.
        popts = ProcessorOptions(rawField, dim, vectorType)
        pipelineReq = PutPipelineRequest(pipeline, s"exact search for ${dist.name}", Processor("elastiknn", popts))
        pipelineRes <- client.execute(pipelineReq)
        _ = pipelineRes.isSuccess shouldBe true

        // Delete the index.
        _ <- client.execute(deleteIndex(index))

        // Create the index.
        createIndexRes <- client.execute(createIndex(index))
        _ = createIndexRes.isSuccess shouldBe true

        // Index the vectors
        indexVecsReqs = testData.corpus.map(ekv => indexVector(index, popts.fieldRaw, ekv, pipeline))
        indexVecsRes <- client.execute(bulk(indexVecsReqs).refresh(RefreshPolicy.IMMEDIATE))
        _ = indexVecsRes.isSuccess shouldBe true
        _ = indexVecsRes.result.errors shouldBe false

        // Run exact query.
        queriesAndResults <- Future.sequence(testData.queries.take(1).map { query =>
          val req = search(index).query(knnQuery(ExactQueryOptions(rawField, dist), query.vector))
          client.execute(req).map(res => query -> res)
        })

        _ = forAll(queriesAndResults) {
          case (query, result) =>
            result.isSuccess shouldBe true
        }

      } yield Succeeded
    }
  }

}
