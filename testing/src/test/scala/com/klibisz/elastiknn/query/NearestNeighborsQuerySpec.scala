package com.klibisz.elastiknn.query

import java.time.Duration

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.client.Elastic4sCompatibility._
import com.klibisz.elastiknn.testing.{ElasticAsyncClient, SilentMatchers}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.XContentFactory
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import org.scalatest.{AsyncFunSpec, Inspectors, Matchers}

import scala.util.Random

class NearestNeighborsQuerySpec extends AsyncFunSpec with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

  // https://github.com/alexklibisz/elastiknn/issues/60
  describe("Vectors in nested fields") {
    implicit val rng: Random = new Random(0)
    val index = "test-queries-nested-fields"
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
        val xcbMapping = XContentFactory.obj()
        val xcbDoc = XContentFactory.obj()
        xcbMapping.startObject("properties")
        subFields.init.foreach { f =>
          xcbMapping.startObject(f)
          xcbMapping.startObject("properties")
          xcbDoc.startObject(f)
        }
        xcbMapping.rawField(subFields.last, ElasticsearchCodec.mapping(mapping).spaces2)
        xcbDoc.rawField(subFields.last, ElasticsearchCodec.vector(vec).spaces2)
        subFields.init.foreach { _ =>
          xcbMapping.endObject()
          xcbMapping.endObject()
          xcbDoc.endObject()
        }
        xcbMapping.endObject()
        xcbDoc.endObject()
        (xcbMapping.string(), xcbDoc.string())
      }
      it(s"works with nested field: $nestedField") {
        for {
          _ <- deleteIfExists(index)
          _ <- eknn.execute(createIndex(index))
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
}
