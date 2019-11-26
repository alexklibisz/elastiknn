package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.Distance._
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.elastic4s._
import com.klibisz.elastiknn.{Distance, ElasticAsyncClient, ExactModelOptions, ProcessorOptions}
import com.sksamuel.elastic4s.ElasticDsl._
import io.circe.Decoder
import io.circe.parser.decode
import org.scalatest._

import scala.concurrent.Future
import scala.io.BufferedSource
import scala.util.Try


class ExactQuerySuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient {

//  private class TestingPipeline(corpusVecs: Seq[Array[Double]], dist: Distance, queryVec: Array[Double], expectedDists: Array[Double]) {
//
//    require(corpusVecs.map(_.length).distinct.length == 1, "All vectors must have same length")
//
//    val index = s"test-exact-${dist.name.toLowerCase}"
//    val procOpts = ProcessorOptions("vecRaw", "vecProc", false, corpusVecs.head.length, ModelOptions.Exact(ExactModelOptions()))
//    val processor = Processor("elastiknn", procOpts)
//    val indexRequests: Seq[IndexRequest] = corpusVecs.zipWithIndex.map {
//      case (v, i) => indexVector(index, procOpts.fieldRaw, v).id(i.toString).pipeline(index)
//    }
//    val getRequests: Seq[GetRequest] = corpusVecs.indices.map(i => get(index, i.toString))
//
//    def ingest(): Future[Assertion] = {
//      val pipelineReq = PipelineRequest(index, Pipeline("exact", Seq(processor)))
//      val createIndexReq = createIndex(index)
//
//      for {
//
//        // Delete the index before running.
//        _ <- client.execute(deleteIndex(index))
//
//        // Hit setup endpoint.
//        setupRes <- client.execute(ElastiKnnSetupRequest())
//        _ = setupRes.isSuccess shouldBe true
//
//        // Create the pipeline.
//        pipelineRes <- client.execute(pipelineReq)
//        _ = pipelineRes.isSuccess shouldBe true
//
//        // Create the index.
//        createRes <- client.execute(createIndexReq)
//        _ = createRes.isSuccess shouldBe true
//
//        // Index documents.
//        indexRes <- client.execute(bulk(indexRequests).refresh(RefreshPolicy.IMMEDIATE))
//        _ = indexRes.isSuccess shouldBe true
//        _ = indexRes.result.errors shouldBe false
//
//      } yield Succeeded
//    }
//  }

  private def readTestData[T](resourceName: String)(implicit ev: Decoder[TestData[T]]): Try[TestData[T]] = {
    val src: BufferedSource = scala.io.Source.fromResource(resourceName)
    val rawJson = try src.mkString finally src.close()
    decode[TestData[T]](rawJson).toTry
  }

  test("parses test data") {
    for {
      testData <- Future.fromTry(readTestData[Double]("distance_angular-10.json"))
    } yield {
      forAll(testData.corpus) { _ should have length 10 }
      forAll(testData.queries) { _.vector should have length 10 }
    }
  }

  for {
    dist <- Distance.values
    dim <- Seq(10, 128, 512)
  } yield {
//    test(s"exact search on $dim-dimensional vectors with $dist distance") {
//
//      val resourceName = s"${dist.name.toLowerCase}-$dim.json"
//      val tryRead = dist match {
//        case DISTANCE_JACCARD | DISTANCE_HAMMING => readTestData[Boolean](resourceName)
//        case _ => readTestData[Double](resourceName)
//      }
//
//      val index = s"test-exact-${dist.name.toLowerCase}"
//
//      for {
//
//        // Read the test data.
//        testData <- Future.fromTry(tryRead)
//
//        // Delete the index before running anything.
//        _ <- client.execute(deleteIndex(index))
//
//        // Hit setup endpoint.
//        setupRes <- client.execute(ElastiKnnSetupRequest())
//        _ = setupRes.isSuccess shouldBe true
//
//        // Create the pipeline.
//        popts = ProcessorOptions("vecRaw", dim, testData.corpus.head.length, ModelOptions.Exact(ExactModelOptions()))
//        processor = Processor("elastiknn", popts)
//        pipelineReq = PipelineRequest(index, Pipeline("exact", Seq(processor)))
//        pipelineRes <- client.execute(pipelineReq)
//        _ = pipelineRes.isSuccess shouldBe true
//
//      } yield Succeeded
//    }
  }

}
