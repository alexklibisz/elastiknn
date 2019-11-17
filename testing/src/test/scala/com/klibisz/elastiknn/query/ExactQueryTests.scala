package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.elastic4s.{Pipeline, PipelineRequest, Processor}
import com.klibisz.elastiknn.{Distance, ElasticAsyncClient, ExactModelOptions, ProcessorOptions}
import org.scalatest.{AsyncFunSuite, Matchers}

import scala.concurrent.Future
import com.sksamuel.elastic4s.ElasticDsl._

class ExactQueryTests extends AsyncFunSuite with Matchers with ElasticAsyncClient {

  private class Test(corpusVecs: Seq[Array[Double]], dist: Distance) {

    require(corpusVecs.map(_.length).distinct.length == 1, "All vectors must have same length")

    val index = s"test-exact-${dist.name.toLowerCase}"
    val procOpts = ProcessorOptions("vecRaw", "vecProc", false, corpusVecs.head.length, ModelOptions.Exact(ExactModelOptions()))
    val processor = Processor("elastiknn", procOpts)

    def ingest(): Future[Unit] = {
      val pipelineReq = PipelineRequest(index, Pipeline("exact", Seq(processor)))
      val createIndexReq = createIndex(index)
      val indexRequests = corpusVecs.zipWithIndex.map()


      ???
    }


  }

  test("setup exact pipeline and search all distances") {




    ???
  }

}
