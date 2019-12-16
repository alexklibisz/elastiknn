package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, QueryOptions}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.client.ElastiKnnClient
import com.klibisz.elastiknn.client.ElastiKnnDsl._
import com.klibisz.elastiknn.{ElastiKnnVector, Elastic4sMatchers, ElasticAsyncClient, ProcessorOptions, Similarity}
import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.common.RefreshPolicy.Immediate
import com.sksamuel.elastic4s.requests.mappings.{BasicField, MappingDefinition}
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import io.circe.parser.decode
import org.scalatest.{Assertion, AsyncTestSuite, Inspectors, Matchers}

import scala.concurrent.Future
import scala.util.{Random, Try}

trait QuerySuite extends Matchers with Inspectors with ElasticAsyncClient with Elastic4sMatchers with ElasticDsl {

  this: AsyncTestSuite =>

  protected def readTestData(sim: Similarity, dim: Int): Try[TestData] =
    for {
      rawJson <- Try {
        val name = s"${sim.name.toLowerCase}-$dim.json"
        val src = scala.io.Source.fromResource(name)
        try src.mkString
        finally src.close()
      }
      dec <- decode[TestData](rawJson).toTry
    } yield dec

  final class Support(rawField: String, sim: Similarity, dim: Int, modelOptions: ModelOptions) {

    val index: String = s"test-${sim.name.toLowerCase}-${System.currentTimeMillis()}"
    val pipelineId: String = s"$index-pipeline"
    val popts: ProcessorOptions = ProcessorOptions(rawField, dim, modelOptions)
    val mapDef: MappingDefinition = MappingDefinition(Seq(BasicField(rawField, "elastiknn_vector")))
    val eknn: ElastiKnnClient = new ElastiKnnClient()

    def corpusId(i: Int): String = s"c$i"
    def queryId(i: Int): String = s"q$i"

    private lazy val setupIndexCorpus: Future[TestData] = for {
      testData <- Future.fromTry(readTestData(sim, dim))
      _ <- client.execute(deleteIndex(index))
      _ <- eknn.setupCluster()
      _ <- eknn.createPipeline(pipelineId, popts)
      _ <- client.execute(createIndex(index).mapping(mapDef))
      corpusIds = testData.corpus.indices.map(corpusId)
      _ <- eknn.indexVectors(index, pipelineId, rawField, testData.corpus, Some(corpusIds), Immediate)
    } yield testData

    final def testGiven(queryOptions: QueryOptions)(fun: Seq[(Query, SearchResponse)] => Future[Assertion]): Future[Assertion] =
      for {
        testData <- setupIndexCorpus
        numHits = testData.queries.head.similarities.length
        queriesResponses <- Future.sequence(testData.queries.map { q =>
          queryOptions match {
            case QueryOptions.Exact(opts) => eknn.knnQuery(index, opts, q.vector, numHits).map(r => q -> r)
          }
        })
        assertion <- fun(queriesResponses)
      } yield assertion

    final def queryIndexed: Seq[(Query, SearchResponse)] = ???

  }

}
