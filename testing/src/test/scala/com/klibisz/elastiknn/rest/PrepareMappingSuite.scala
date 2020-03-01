package com.klibisz.elastiknn.rest

import com.klibisz.elastiknn.ProcessorOptions.{JaccardLshModelOptions, ModelOptions}
import com.klibisz.elastiknn.client.ElastiKnnClient
import com.klibisz.elastiknn.{Elastic4sMatchers, ElasticAsyncClient, ProcessorOptions}
import com.sksamuel.elastic4s.ElasticDsl
import io.circe.parser._
import org.scalatest.{AsyncFunSuite, Matchers}

import scala.concurrent.Future

class PrepareMappingSuite extends AsyncFunSuite with Matchers with Elastic4sMatchers with ElasticAsyncClient with ElasticDsl {

  test("prepare mapping works with existing field") {
    val eknn = new ElastiKnnClient()
    val index = "test-prepare-mapping"
    val rawField = "the_raw_field"
    val procField = "the_processed_field"
    val existingField = "existing_field"
    val correctMappingStr =
      s"""
        |{
        |  "$index": {
        |    "mappings": {
        |      "properties": {
        |        "$rawField": {
        |          "type": "elastiknn_vector"
        |        },
        |        "$procField": {
        |          "type": "text",
        |          "similarity": "boolean",
        |          "analyzer": "whitespace"
        |        },
        |        "$existingField": {
        |          "type": "long"
        |        }
        |      }
        |    }
        |  }
        |}
        |""".stripMargin
    for {
      correctMapping <- Future.fromTry(parse(correctMappingStr).toTry)
      _ <- client.execute(deleteIndex(index))
      _ <- client.execute(createIndex(index))
      _ <- client.execute(putMapping(index).fields(longField(existingField)))
      _ <- eknn.prepareMapping(index, ProcessorOptions(rawField, 9, ModelOptions.JaccardLsh(JaccardLshModelOptions(0, procField))))
      getMapping <- client.execute(getMapping(index))
      _ = getMapping.shouldBeSuccess
      _ = getMapping.body shouldBe defined
      actualMapping <- Future.fromTry(parse(getMapping.body.get).toTry)
    } yield {
      getMapping.body shouldBe defined
      actualMapping shouldBe correctMapping
    }
  }

}
