package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.elastic4s._
import com.klibisz.elastiknn.{Distance, ElasticAsyncClient, ExactModelOptions, ProcessorOptions}
import org.scalatest.{Assertion, AsyncFunSuite, Matchers, Succeeded}

import scala.concurrent.Future
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.get.GetRequest
import com.sksamuel.elastic4s.requests.indexes.IndexRequest


class ExactQueryTests extends AsyncFunSuite with Matchers with ElasticAsyncClient {

  private class Pipeline(corpusVecs: Seq[Array[Double]], dist: Distance) {

    require(corpusVecs.map(_.length).distinct.length == 1, "All vectors must have same length")

    val index = s"test-exact-${dist.name.toLowerCase}"
    val procOpts = ProcessorOptions("vecRaw", "vecProc", false, corpusVecs.head.length, ModelOptions.Exact(ExactModelOptions()))
    val processor = Processor("elastiknn", procOpts)
    val indexRequests: Seq[IndexRequest] = corpusVecs.zipWithIndex.map {
      case (v, i) => indexVector(index, procOpts.fieldRaw, v).id(i.toString).pipeline(index)
    }
    val getRequests: Seq[GetRequest] = corpusVecs.indices.map(i => get(index, i.toString))

    def ingest(): Future[Assertion] = {
      val pipelineReq = PipelineRequest(index, Pipeline("exact", Seq(processor)))
      val createIndexReq = createIndex(index)

      for {

        // Delete the index before running.
        _ <- client.execute(deleteIndex(index))

        // Hit setup endpoint.
        setupRes <- client.execute(ElastiKnnSetupRequest())
        _ = setupRes.isSuccess shouldBe true

        // Create the pipeline.
        pipelineRes <- client.execute(pipelineReq)
        _ = pipelineRes.isSuccess shouldBe true

        // Create the index.
        createRes <- client.execute(createIndexReq)
        _ = createRes.isSuccess shouldBe true

        // Index documents.
        indexRes <- client.execute(bulk(indexRequests).refresh(RefreshPolicy.IMMEDIATE))
        _ = indexRes.isSuccess shouldBe true
        _ = indexRes.result.errors shouldBe false

      } yield Succeeded
    }


  }

  test("setup exact pipeline and search all distances") {
    val pipeline = new Pipeline()

    for {

    }

    ???
  }

}
