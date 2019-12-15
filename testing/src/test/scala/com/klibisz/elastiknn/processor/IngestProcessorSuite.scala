package com.klibisz.elastiknn.processor

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.VectorType.VECTOR_TYPE_FLOAT
import com.klibisz.elastiknn.elastic4s.{Processor, PutPipelineRequest}
import com.klibisz.elastiknn._
import org.scalatest.{AsyncFunSuite, Matchers}

class IngestProcessorSuite extends AsyncFunSuite with Matchers with Elastic4sMatchers with ElasticAsyncClient {

  test("make an exact pipeline") {
    val opts = ProcessorOptions("a", 32, VECTOR_TYPE_FLOAT, ModelOptions.Exact(ExactModelOptions()))
    val req = PutPipelineRequest("exact", "exact knn pipeline", Processor("elastiknn", opts))
    for {
      res <- client.execute(req)
    } yield {
      res.shouldBeSuccess
      res.result.acknowledged shouldBe true
    }
  }

  test("make an lsh pipeline") {
    val opts = ProcessorOptions("a", 32, VECTOR_TYPE_FLOAT, ModelOptions.Lsh(LshModelOptions()))
    val req = PutPipelineRequest("lsh", "d", Processor("elastiknn", opts))
    for {
      res <- client.execute(req)
    } yield {
      res.shouldBeSuccess
      res.result.acknowledged shouldBe true
    }
  }

}
