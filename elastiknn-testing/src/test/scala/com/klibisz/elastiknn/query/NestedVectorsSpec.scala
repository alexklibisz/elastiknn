package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.client.Elastic4sCompatibility._
import com.klibisz.elastiknn.testing.ElasticAsyncClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.XContentFactory
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import org.scalatest._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class NestedVectorsSpec extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient {

  // https://github.com/alexklibisz/elastiknn/issues/60
  implicit val rng: Random = new Random(0)
  val index = "issue-60"
  val vec = Vec.DenseFloat.random(10)
  val mapping = Mapping.DenseFloat(vec.values.length)
  val nestedFields = Seq(
    "vec",
    "foo.vec",
    "foo.bar.vec",
    "foo.bar.baz.vec"
  )

  for {
    nestedField <- nestedFields
  } yield {
    val (mappingSource, docSource) = {
      val subFields = nestedField.split('.')
      val xMapping = XContentFactory.obj()
      val xDoc = XContentFactory.obj()
      xMapping.startObject("properties")
      subFields.init.foreach { f =>
        xMapping.startObject(f)
        xMapping.startObject("properties")
        xDoc.startObject(f)
      }
      xMapping.rawField(subFields.last, ElasticsearchCodec.mapping(mapping).spaces2)
      xDoc.rawField(subFields.last, ElasticsearchCodec.vec(vec).spaces2)
      subFields.init.foreach { _ =>
        xMapping.endObject()
        xMapping.endObject()
        xDoc.endObject()
      }
      xMapping.endObject()
      xDoc.endObject()
      (xMapping.string(), xDoc.string())
    }
    test(s"works with nested field: $nestedField") {
      for {
        _ <- deleteIfExists(index)
        _ <- eknn.createIndex(index)
        _ <- eknn.execute(putMapping(index).rawSource(mappingSource))
        _ <- eknn.execute(indexInto(index).source(docSource).refresh(RefreshPolicy.IMMEDIATE))
        res <- eknn.execute(search(index).query(NearestNeighborsQuery.Exact(nestedField, Similarity.L2, vec)))
      } yield {
        res.result.hits.hits should have length 1
        res.result.hits.maxScore shouldBe 1.0
      }
    }
  }

}
