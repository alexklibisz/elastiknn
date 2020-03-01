package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.KNearestNeighborsQuery._
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.client.ElastiKnnClient
import com.klibisz.elastiknn._
import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.common.RefreshPolicy.Immediate
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import io.circe.parser.decode
import org.scalatest.{AsyncTestSuite, Assertion}

import scala.concurrent.Future
import scala.util.Try

trait QuerySuite extends ElasticAsyncClient with ElasticDsl {

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

  final def testDataDims: Seq[Int] = Seq(10, 128, 512)

  final class Harness[M: ModelOptionsLike](similarity: Similarity,
                                           fieldRaw: String,
                                           dimension: Int,
                                           index: String,
                                           pipelineId: String,
                                           modelOptions: M) {

    val popts: ProcessorOptions = ProcessorOptions(fieldRaw, dimension, implicitly[ModelOptionsLike[M]].apply(modelOptions))
    val eknn: ElastiKnnClient = new ElastiKnnClient()
    val queryVectorIdPrefix: String = "q"
    val corpusVectorIdPrefix: String = "c"

    def corpusId(i: Int): String = s"$corpusVectorIdPrefix$i"
    def queryId(i: Int): String = s"$queryVectorIdPrefix$i"

    private lazy val setupIndexCorpus: Future[TestData] = for {
      testData <- Future.fromTry(readTestData(similarity, dimension))
      _ <- client.execute(deleteIndex(index))
      _ <- client.execute(createIndex(index))
      _ <- eknn.createPipeline(pipelineId, popts)
      _ <- eknn.prepareMapping(index, popts)
      corpusIds = testData.corpus.indices.map(corpusId)
      _ <- eknn.indexVectors(index, pipelineId, fieldRaw, testData.corpus, Some(corpusIds), Immediate)
    } yield testData

    lazy val numHits: Future[Int] = for (testData <- setupIndexCorpus)
      yield testData.queries.head.similarities.length

    /** Runs a test for queries which just take an [[ElastiKnnVector]]. Passes the query and response to an assertion. */
    def testGiven[O: QueryOptionsLike](queryOptions: O, useCache: Boolean)(
        fun: Seq[(Query, SearchResponse)] => Assertion): Future[Assertion] =
      for {
        testData <- setupIndexCorpus
        numHits <- this.numHits
        queriesResponses <- Future.sequence(testData.queries.map { q =>
          eknn.knnQuery(index, pipelineId, queryOptions, q.vector, numHits, useCache).map(r => q -> r)
        })
      } yield fun(queriesResponses)

    /** Run tests for queries which take an [[IndexedQueryVector]]. Passes the query, query vector ID, and response to an assertion. */
    def testIndexed[O: QueryOptionsLike](queryOptions: O, useCache: Boolean)(
        fun: Seq[(Query, String, SearchResponse)] => Assertion): Future[Assertion] =
      for {
        testData <- setupIndexCorpus
        numHits <- numHits.map(_ + testData.queries.length + 1)
        queryIds = testData.queries.indices.map(queryId)
        _ <- eknn.indexVectors(index, pipelineId, popts.fieldRaw, testData.queries, ids = Some(queryIds), Immediate)
        queriesAndResponses <- Future.sequence(testData.queries.zipWithIndex.map {
          case (q, i) =>
            val iqv = IndexedQueryVector(index, fieldRaw, queryId(i))
            eknn.knnQuery(index, pipelineId, queryOptions, iqv, numHits, useCache).map(r => (q, queryId(i), r))
        })
      } yield fun(queriesAndResponses)

  }

//  final class Support(rawField: String, sim: Similarity, dim: Int, modelOptions: ModelOptions) {
//
//    val index: String = s"test-${sim.name.toLowerCase}-$dim"
//    val pipelineId: String = s"$index-pipeline-${modelOptions.hashCode.abs}"
//    val popts: ProcessorOptions = ProcessorOptions(rawField, dim, modelOptions)
//    val eknn: ElastiKnnClient = new ElastiKnnClient()
//    val queryVectorIdPrefix: String = "q"
//    val corpusVectorIdPrefix: String = "c"
//
//    def corpusId(i: Int): String = s"$corpusVectorIdPrefix$i"
//    def queryId(i: Int): String = s"$queryVectorIdPrefix$i"
//
//    private lazy val setupIndexCorpus: Future[TestData] = for {
//      testData <- Future.fromTry(readTestData(sim, dim))
//      _ <- client.execute(deleteIndex(index))
//      _ <- client.execute(createIndex(index))
//      _ <- eknn.createPipeline(pipelineId, popts)
//      _ <- eknn.prepareMapping(index, popts)
//      corpusIds = testData.corpus.indices.map(corpusId)
//      _ <- eknn.indexVectors(index, pipelineId, rawField, testData.corpus, Some(corpusIds), Immediate)
//    } yield testData
//
//    lazy val numHits: Future[Int] = for (testData <- setupIndexCorpus)
//      yield testData.queries.head.similarities.length
//
//    /** Runs a test for queries which just take an [[ElastiKnnVector]]. Passes the query and response to an assertion. */
//    def testGiven[O: QueryOptionsLike](queryOptions: O, useCache: Boolean)(
//        fun: Seq[(Query, SearchResponse)] => Assertion): Future[Assertion] =
//      for {
//        testData <- setupIndexCorpus
//        numHits <- this.numHits
//        queriesResponses <- Future.sequence(testData.queries.map { q =>
//          implicitly[QueryOptionsLike[O]].apply(queryOptions) match {
//            case QueryOptions.ExactComputed(opts) => eknn.knnQuery(index, opts, q, numHits, useCache).map(r => q -> r)
//            case QueryOptions.Lsh(opts)           => eknn.knnQuery(index, opts, q, numHits, useCache).map(r => q -> r)
//            case _                                => Future.failed(illArgEx("query options must be exact or lsh"))
//          }
//        })
//      } yield fun(queriesResponses)
//
//    /** Run tests for queries which take an [[IndexedQueryVector]]. Passes the query, query vector ID, and response to an assertion. */
//    def testIndexed[O: QueryOptionsLike](queryOptions: O, useCache: Boolean)(
//        fun: Seq[(Query, String, SearchResponse)] => Assertion): Future[Assertion] =
//      for {
//        testData <- setupIndexCorpus
//        numHits <- numHits.map(_ + testData.queries.length + 1)
//        queryIds = testData.queries.indices.map(queryId)
//        _ <- eknn.indexVectors(index, pipelineId, popts.fieldRaw, testData.queries, ids = Some(queryIds), Immediate)
//        queriesAndResponses <- Future.sequence(testData.queries.zipWithIndex.map {
//          case (q, i) =>
//            val iqv = IndexedQueryVector(index, rawField, queryId(i))
//            implicitly[QueryOptionsLike[O]].apply(queryOptions) match {
//              case QueryOptions.ExactComputed(opts) =>
//                eknn.knnQuery(index, opts, iqv, numHits, useCache).map(r => (q, queryId(i), r))
//              case QueryOptions.Lsh(opts) =>
//                eknn.knnQuery(index, opts, iqv, numHits, useCache).map(r => (q, queryId(i), r))
//              case _ => Future.failed(illArgEx("query options must be exact or lsh"))
//            }
//        })
//      } yield fun(queriesAndResponses)
//
//  }

}
