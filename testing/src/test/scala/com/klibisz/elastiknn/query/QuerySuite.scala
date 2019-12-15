package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.client.ElastiKnnDsl
import com.klibisz.elastiknn.{Elastic4sMatchers, ElasticAsyncClient, ProcessorOptions, Similarity}
import com.sksamuel.elastic4s.requests.mappings.{BasicField, MappingDefinition}
import com.sksamuel.elastic4s.ElasticDsl._
import io.circe.parser.decode
import org.scalatest.{AsyncTestSuite, Inspectors, Matchers}

import scala.concurrent.Future
import scala.util.{Random, Try}

trait QuerySuite extends Matchers with Inspectors with ElasticAsyncClient with Elastic4sMatchers with ElastiKnnDsl {

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

  def setupAndIndex(sim: Similarity, dim: Int, modelOptions: ModelOptions) = {

    val tryReadData = readTestData(sim, dim)

    val index = s"test-${sim.name.toLowerCase}-${Random.nextInt(Int.MaxValue)}"
    val pipeline = s"$index-pipeline"
    val rawField = "vec"
    val mapDef = MappingDefinition(Seq(BasicField(rawField, "elastiknn_vector")))

    def corpusId(i: Int): String = s"c$i"
    def queryId(i: Int): String = s"q$i"

    for {
      // Read test data.
      testData <- Future.fromTry(tryReadData)
      numHits = testData.queries.head.similarities.length

      // Delete index before running.
      _ <- client.execute(deleteIndex(index))

      // Hit setup endpoint.
      setupRes <- client.execute(ElastiKnnSetupRequest())
      _ = setupRes.shouldBeSuccess

      // Create the pipeline.
      popts = ProcessorOptions(rawField, dim, modelOptions)

    } yield ???

  }

}
