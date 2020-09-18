package com.klibisz.elastiknn.query

import java.util.UUID

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

  // https://github.com/alexklibisz/elastiknn/issues/60
  describe("Vectors in nested fields") {
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

  // https://github.com/alexklibisz/elastiknn/issues/97
  describe("Query with filter on another field") {
    implicit val rng: Random = new Random(0)
    val indexPrefix = "issue-97"

    // Generate a corpus of 100 docs. < 10 of the them have color blue, rest have color red.
    val dims = 10
    val numBlue = 8
    val corpus = (0 until 100).map(i => (s"v$i", Vec.DenseFloat.random(dims), if (i < numBlue) "blue" else "red"))

    // Test with multiple mappings/queries.
    val queryVec = Vec.DenseFloat.random(dims)
    val mappingsAndQueries = Seq(
      Mapping.L2Lsh(dims, 40, 1, 2) -> Seq(
        NearestNeighborsQuery.Exact("vec", Similarity.L2, queryVec),
        NearestNeighborsQuery.Exact("vec", Similarity.Angular, queryVec),
        NearestNeighborsQuery.L2Lsh("vec", 100, vec = queryVec)
      ),
      Mapping.AngularLsh(dims, 40, 1) -> Seq(
        NearestNeighborsQuery.Exact("vec", Similarity.L2, queryVec),
        NearestNeighborsQuery.Exact("vec", Similarity.Angular, queryVec),
        NearestNeighborsQuery.AngularLsh("vec", 100, queryVec)
      )
    )

    for {
      (mapping, queries) <- mappingsAndQueries
      query: NearestNeighborsQuery <- queries
    } it(s"filters with mapping [${mapping}] and query [${query}]") {
      val index = s"$indexPrefix-${UUID.randomUUID.toString}"
      val rawMapping =
        s"""
           |{
           |  "properties": {
           |    "vec": ${ElasticsearchCodec.mapping(mapping).noSpaces},
           |    "color": {
           |      "type": "keyword"
           |    }
           |  }
           |}
           |""".stripMargin
      val rawQuery =
        s"""
           |{
           |  "bool": {
           |    "filter": [
           |      { "term": { "color": "blue" } }
           |    ],
           |    "must": {
           |      "elastiknn_nearest_neighbors": ${ElasticsearchCodec.nearestNeighborsQuery(query).spaces4}
           |    }
           |  }
           |}
           |""".stripMargin
      for {
        _ <- deleteIfExists(index)
        _ <- eknn.execute(createIndex(index).shards(1).replicas(1))
        _ <- eknn.execute(putMapping(index).rawSource(rawMapping))
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
        res <- eknn.execute(search(index).rawQuery(rawQuery))
      } yield {
        res.result.hits.hits should have length numBlue
        res.result.hits.hits.map(_.id).toSet shouldBe corpus.filter(_._3 == "blue").map(_._1).toSet
      }
    }
  }

  // https://gitter.im/elastiknn/community?at=5f3012df65e829425e70ee31
  describe("Sparse bool vectors with unsorted indices") {
    implicit val rng: Random = new Random(0)
    val indexPrefix = "test-sbv-unsorted"

    val dims = 20000
    val corpus = Vec.SparseBool.randoms(dims, 100)

    val queryVec = {
      val sorted = corpus.head
      val shuffled = rng.shuffle(sorted.trueIndices.toVector).toArray
      sorted.copy(shuffled)
    }

    // Test with multiple mappings/queries.
    val mappingsAndQueries = Seq(
      Mapping.SparseBool(dims) -> Seq(
        NearestNeighborsQuery.Exact("vec", Similarity.Jaccard, queryVec),
        NearestNeighborsQuery.Exact("vec", Similarity.Hamming, queryVec),
      ),
      Mapping.JaccardLsh(dims, 40, 1) -> Seq(
        NearestNeighborsQuery.Exact("vec", Similarity.Jaccard, queryVec),
        NearestNeighborsQuery.Exact("vec", Similarity.Hamming, queryVec),
        NearestNeighborsQuery.JaccardLsh("vec", 100, queryVec)
      ),
      Mapping.HammingLsh(dims, 40, 2) -> Seq(
        NearestNeighborsQuery.Exact("vec", Similarity.Jaccard, queryVec),
        NearestNeighborsQuery.Exact("vec", Similarity.Hamming, queryVec),
        NearestNeighborsQuery.HammingLsh("vec", 100, queryVec)
      )
    )

    for {
      (mapping, queries) <- mappingsAndQueries
      query <- queries
    } it(s"finds unsorted sparse bool vecs with mapping [${mapping}] and query [${query}]") {
      val index = s"$indexPrefix-${UUID.randomUUID.toString}"
      for {
        _ <- deleteIfExists(index)
        _ <- eknn.execute(createIndex(index).shards(1).replicas(1))
        _ <- eknn.putMapping(index, "vec", "id", mapping)
        _ <- eknn.index(index, "vec", corpus, "id", corpus.indices.map(i => s"v$i"))
        _ <- eknn.execute(refreshIndex(index))
        res <- eknn.nearestNeighbors(index, query, 5, "id")
      } yield {
        res.result.maxScore shouldBe 1d
        res.result.hits.hits.head.id shouldBe "v0"
      }
    }
  }

  describe("deleting vectors") {

    // https://github.com/alexklibisz/elastiknn/issues/158
    it("index, search, delete some, search again") {

      implicit val rng: Random = new Random(0)
      val index = "issue-158"
      val dims = 100
      val corpus = Vec.DenseFloat.randoms(dims, 1000)
      val ids = corpus.indices.map(i => s"v$i")
      val queryVec = corpus.head
      val mapping = Mapping.L2Lsh(dims, 40, 4, 2)
      val query = NearestNeighborsQuery.L2Lsh("vec", 30, 1, queryVec)

      for {
        _ <- deleteIfExists(index)
        _ <- eknn.execute(createIndex(index).shards(1).replicas(0))
        _ <- eknn.putMapping(index, "vec", "id", mapping)
        _ <- eknn.index(index, "vec", corpus, "id", ids)
        _ <- eknn.execute(refreshIndex(index))
        _ <- eknn.execute(forceMerge(index).maxSegments(1))

        // Check the count before deleting anything.
        cnt1 <- eknn.execute(count(index))
        res1 <- eknn.nearestNeighbors(index, query, 10, "id")

        // Delete the query vector from the index, count, and search again.
        _ <- eknn.execute(deleteById(index, "v0"))
        _ <- eknn.execute(refreshIndex(index))
        cnt2 <- eknn.execute(count(index))
        res2 <- eknn.nearestNeighbors(index, query, 10, "id")

        // Put the vector back, count, search again.
        _ <- eknn.index(index, "vec", corpus.take(1), "id", ids.take(1))
        _ <- eknn.execute(refreshIndex(index))
        cnt3 <- eknn.execute(count(index))
        res3 <- eknn.nearestNeighbors(index, query, 10, "id")

      } yield {
        cnt1.result.count shouldBe 1000L
        res1.result.maxScore shouldBe 1d
        res1.result.hits.hits.head.id shouldBe "v0"

        cnt2.result.count shouldBe 999L
        res2.result.hits.hits.head.id shouldBe res1.result.hits.hits.tail.head.id

        cnt3.result.count shouldBe 1000L
        res3.result.hits.hits.head.id shouldBe "v0"
      }
    }
  }

}
