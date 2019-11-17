package com.klibisz.elastiknn

import java.util

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.test.ESIntegTestCase
import org.junit.Before

//@ClusterScope(scope = Scope.SUITE)
@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // https://discuss.elastic.co/t/integration-testing-in-java-carrotsearch-thread-leaks-severe-errors/26831/4
class ElastiKnnClusterIT extends ESIntegTestCase with TestingUtils {

  @Before
  override def setUp(): Unit = {
    super.setUp()
    this.ensureGreen()
  }

  override def nodePlugins(): util.Collection[Class[_ <: Plugin]] = {
    util.Arrays.asList(classOf[ElastiKnnPlugin])
  }

//  def helperVecToSource(field: String, vec: Array[Double]): Json = Json.fromJsonObject(
//    JsonObject(field -> Json.fromValues(vec.map(Json.fromDoubleOrNull)))
//  )
//
//  def helperTestExact(vecs: Seq[Array[Double]], dist: Distance, queryVector: Array[Double], expectedDists: Array[Double]): Future[Unit] = {
//
//    require(vecs.nonEmpty)
//    require(vecs.map(_.length).distinct.length == 1)
//
//    val (fieldRaw, fieldProc) = ("vecRaw", "vecProc")
//    val index = s"elastiknn-exact-${dist.value}"
//    val processor = Processor("elastiknn", ProcessorOptions("vecRaw", "vecProc", false, vecs.head.length, Exact(ExactModelOptions())))
//    val pipelineRequest = PipelineRequest(index, Pipeline("exact", Seq(processor)))
//    val createIndexRequest = ElasticDsl.createIndex(name = index)
//    val indexRequests = vecs.zipWithIndex.map {
//      case (v: Array[Double], i: Int) =>
//        indexInto(index).id(i.toString).source(helperVecToSource(fieldRaw, v).noSpaces).pipeline(index)
//    }
//    val getRequests = vecs.indices.map { i =>
//      get(index, i.toString)
//    }
//
//    val searchGivenQuery = KNearestNeighborsQuery(
//      pipelineId = pipelineRequest.name,
//      processorId = processor.name,
//      k = vecs.length,
//      queryOptions = QueryOptions.Exact(
//        ExactQueryOptions(
//          distance = dist
//        )),
//      queryVector = QueryVector.Given(
//        GivenQueryVector(
//          queryVector
//        ))
//    )
//
//    val searchGivenRequest = search(index).query(new CustomQuery {
//      def buildQueryBody(): XContentBuilder =
//        XContentFactory.parse(
//          JsonObject(
//            "elastiknn_knn" -> JsonFormat.toJson(searchGivenQuery)
//          ).asJson.noSpaces)
//    })
//
//    for {
//
//      // Hit the setup endpoint.
//      setupResponse <- client.execute(ElastiKnnSetupRequest())
////      _ = assertTrue(setupResponse.isSuccess)
//
//      // Create the pipeline.
//      pipelineResponse <- client.execute(pipelineRequest)
//      _ = assertTrue(pipelineResponse.isSuccess)
//      _ = assertTrue(pipelineResponse.result.acknowledged)
//
//      // Create the index and mapping.
//      createIndexResponse <- client.execute(createIndexRequest)
//      _ = assertTrue(createIndexResponse.isSuccess)
//
//      // Index the vectors.
//      indexResponse <- client.execute(bulk(indexRequests).refresh(RefreshPolicy.IMMEDIATE))
//      _ = assertTrue(indexResponse.isSuccess)
//      _ = assertFalse(indexResponse.result.errors)
//
//      // Get the vectors and check they contain the correct structure.
//      // Note the current implementation only works when fieldProc is at the top level.
//      getResponses <- Future.sequence(getRequests.map(client.execute(_)))
//      _ = assertEquals(getResponses.map(_.isSuccess), getResponses.map(_ => true))
//      _ = getResponses.sortBy(_.result.id).zip(vecs).foreach {
//        case (r, v) =>
//          val j = parser.parse(r.result.sourceAsString).toTry.get
//          assert(j.findAllByKey(fieldProc).nonEmpty)
//          val pv = JsonFormat.fromJson[ProcessedVector](j.findAllByKey(fieldProc).head)
//          assertEquals(pv.getExact.vector.toSeq, v.toSeq)
//      }
//
//      // Run a query with a given vector.
//      searchGivenResponse <- client.execute(searchGivenRequest)
////      _ = assertTrue(searchGivenResponse.result.toString, searchGivenResponse.isSuccess)
//
//    } yield ()
//  }
//
//  def testExactPipelineAndSearchAllDistances(): Unit = await {
//    helperTestExact(Seq(Array(-0.1, 0.05, 0.11), Array(-0.3, -0.2, 0.5)),
//                    DISTANCE_ANGULAR,
//                    Array(-0.1, 0.2, 0.3),
//                    Array(0.90311758, 0.60697698).map(_ + 1.0))
//  }

}
