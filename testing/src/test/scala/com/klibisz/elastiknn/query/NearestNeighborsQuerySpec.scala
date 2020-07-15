package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.client.Elastic4sCompatibility._
import com.klibisz.elastiknn.testing.{ElasticAsyncClient, SilentMatchers}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.XContentFactory
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import org.scalatest.{AsyncFunSpec, Inspectors, Matchers}

import scala.concurrent.Future
import scala.util.Random

class NearestNeighborsQuerySpec extends AsyncFunSpec with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

//  // https://github.com/alexklibisz/elastiknn/issues/60
//  describe("Vectors in nested fields") {
//    implicit val rng: Random = new Random(0)
//    val index = "test-queries-nested-fields"
//    val vec = Vec.DenseFloat.random(10)
//    val mapping = Mapping.DenseFloat(vec.values.length)
//    val nestedFields = Seq(
//      "vec",
//      "foo.vec",
//      "foo.bar.vec",
//      "foo.bar.baz.vec"
//    )
//
//    for {
//      nestedField <- nestedFields
//    } yield {
//      val (mappingSource, docSource) = {
//        val subFields = nestedField.split('.')
//        val xMapping = XContentFactory.obj()
//        val xDoc = XContentFactory.obj()
//        xMapping.startObject("properties")
//        subFields.init.foreach { f =>
//          xMapping.startObject(f)
//          xMapping.startObject("properties")
//          xDoc.startObject(f)
//        }
//        xMapping.rawField(subFields.last, ElasticsearchCodec.mapping(mapping).spaces2)
//        xDoc.rawField(subFields.last, ElasticsearchCodec.vector(vec).spaces2)
//        subFields.init.foreach { _ =>
//          xMapping.endObject()
//          xMapping.endObject()
//          xDoc.endObject()
//        }
//        xMapping.endObject()
//        xDoc.endObject()
//        (xMapping.string(), xDoc.string())
//      }
//      it(s"works with nested field: $nestedField") {
//        for {
//          _ <- deleteIfExists(index)
//          _ <- eknn.execute(createIndex(index))
//          _ <- eknn.execute(putMapping(index).rawSource(mappingSource))
//          _ <- eknn.execute(indexInto(index).source(docSource).refresh(RefreshPolicy.IMMEDIATE))
//          res <- eknn.execute(search(index).query(NearestNeighborsQuery.Exact(nestedField, Similarity.L2, vec)))
//        } yield {
//          res.result.hits.hits should have length 1
//          res.result.hits.maxScore shouldBe 1.0
//        }
//      }
//    }
//  }

  describe("Query with filter on another field") {

    it("filters docs by another field before running elastiknn_nearest_neighbors query") {
      val index = "test-queries-with-filter"
      val mappingSource =
        s"""
          |{
          |  "properties": {
          |    "vec": ${ElasticsearchCodec.mapping(Mapping.DenseFloat(4)).noSpaces},
          |    "color": {
          |      "type": "keyword"
          |    }
          |  }
          |}
          |""".stripMargin
      val corpus = Seq(
        ("v1", Vec.DenseFloat(Array(0.1f, 0.2f, 0.3f, 0.5f)), "blue"),
        ("v2", Vec.DenseFloat(Array(0.1f, 0.2f, 0.3f, 0.6f)), "green"),
        ("v3", Vec.DenseFloat(Array(0.1f, 0.3f, 0.3f, 0.7f)), "blue"),
        ("v4", Vec.DenseFloat(Array(0.1f, 0.2f, 0.3f, 0.8f)), "red")
      )
      val querySource =
        s"""
           |{
           |  "elastiknn_nearest_neighbors": {
           |    "field": "vec",
           |    "vec": {
           |      "values": [0.1, 0.2, 0.3, 0.4]
           |    },
           |    "model": "exact",
           |    "similarity": "l2",
           |    "filter": [
           |      { "term": {"color": "blue"} }
           |    ]
           |  }
           |}
           |""".stripMargin
      for {
        _ <- deleteIfExists(index)
        _ <- eknn.execute(createIndex(index).shards(1).replicas(1))
        _ <- eknn.execute(putMapping(index).rawSource(mappingSource))
        _ <- Future.traverse(corpus) {
          case (id, vec, color) =>
            val docSource =
              s"""
                 |{
                 |  "vec": ${ElasticsearchCodec.nospaces(vec)},
                 |  "color": "$color"
                 |}
                 |""".stripMargin
            eknn.execute(indexInto(index).id(id).source(docSource))
        }
        _ <- eknn.execute(refreshIndex(index))
        res <- eknn.execute(search(index).rawQuery(querySource))
      } yield {
        println(res)
        ???
      }
    }

  }

}
