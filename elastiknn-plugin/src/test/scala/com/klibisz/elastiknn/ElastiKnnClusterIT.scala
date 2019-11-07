package com.klibisz.elastiknn

import java.util
import java.util.Collections

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope
import com.klibisz.elastiknn.Distance.DISTANCE_L2
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions.{Exact, Lsh}
import com.klibisz.elastiknn.utils.Elastic4sUtils._
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import io.circe.{Json, JsonObject, parser}
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.test.ESIntegTestCase
import org.junit.Assert._
import org.junit.Before
import scalapb_circe.JsonFormat

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // https://discuss.elastic.co/t/integration-testing-in-java-carrotsearch-thread-leaks-severe-errors/26831/4
class ElastiKnnClusterIT extends ESIntegTestCase with TestingUtils {

  private lazy val client: ElasticClient = elasticClient(ESIntegTestCase.getRestClient)

  @Before
  override def setUp(): Unit = {
    this.ensureGreen()
    super.setUp()
  }

  override def nodePlugins(): util.Collection[Class[_ <: Plugin]] =
    Collections.singletonList(classOf[ElastiKnnPlugin])

  def testPluginsInstalled(): Unit = await {
    client.execute(catPlugins()).map { res =>
      assertEquals(res.result.length, 1)
      assertEquals(res.result.head.component, "elastiknn")
    }
  }

  def testMakeExactPipeline(): Unit = await {
    val opts = ProcessorOptions("a", "b", 32, Exact(ExactModelOptions()))
    val req = PipelineRequest("exact", Pipeline("d", Seq(Processor("elastiknn", opts))))
    client.execute(req).map { res =>
      assertTrue(res.isSuccess)
      assertTrue(res.result.acknowledged)
    }
  }

  def testMakeLshPipeline(): Unit = await {
    val opts = ProcessorOptions("a", "b", 32, Lsh(LshModelOptions(k = 10, l = 20)))
    val req = PipelineRequest("lsh", Pipeline("d", Seq(Processor("elastiknn", opts))))
    client.execute(req).map { res =>
      assertTrue(res.isSuccess)
      assertTrue(res.result.acknowledged)
    }
  }

  def helperVecToSource(field: String, vec: Array[Double]): Json = Json.fromJsonObject(
    JsonObject(field -> Json.fromValues(vec.map(Json.fromDoubleOrNull)))
  )

  def helperTestExact(vecs: Seq[Array[Double]], dist: Distance, query: Array[Double], expectedDists: Array[Double]): Future[Unit] = {

    require(vecs.nonEmpty)
    require(vecs.map(_.length).distinct.length == 1)

    val (fieldRaw, fieldProc) = ("vecRaw", "vecProc")
    val index = s"elastiknn-exact-${dist.value}"
    val processor = Processor("elastiknn", ProcessorOptions("vecRaw", "vecProc", vecs.head.length, Exact(ExactModelOptions())))
    val pipelineRequest = PipelineRequest(index, Pipeline("exact", Seq(processor)))
    val indexRequests = vecs.zipWithIndex.map {
      case (v: Array[Double], i: Int) =>
        indexInto(index).id(i.toString).source(helperVecToSource(fieldRaw, v).noSpaces).pipeline(index)
    }
    val getRequests = vecs.indices.map { i =>
      get(index, i.toString)
    }

    for {

      // Create the pipeline.
      pipelineResponse <- client.execute(pipelineRequest)
      _ = assertTrue(pipelineResponse.isSuccess)
      _ = assertTrue(pipelineResponse.result.acknowledged)

      // Index the vectors.
      indexResponse <- client.execute(bulk(indexRequests))
      _ = assertTrue(indexResponse.isSuccess)
      _ = assertFalse(indexResponse.result.errors)

      // Get the vectors and check they contain the correct structure.
      // Note the current implementation only works when fieldProc is at the top level.
      getResponses <- Future.sequence(getRequests.map(client.execute(_)))
      _ = assertEquals(getResponses.map(_.isSuccess), getResponses.map(_ => true))
      _ = getResponses.sortBy(_.result.id).zip(vecs).foreach {
        case (r, v) =>
          val j = parser.parse(r.result.sourceAsString).toTry.get
          assert(j.findAllByKey(fieldProc).nonEmpty)
          val pv = JsonFormat.fromJson[ProcessedVector](j.findAllByKey(fieldProc).head)
          assertEquals(pv.getExact.vector.toSeq, v.toSeq)
      }

    } yield ()
  }

  def testExactPipelineAndSearchAllDistances(): Unit = await {
    helperTestExact(Seq(Array(0.1, 0.2), Array(0.22, 0.4)), DISTANCE_L2, Array.empty, Array.empty)
  }

}
