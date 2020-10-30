package com.klibisz.elastiknn.query

import java.util.UUID

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.client.Elastic4sCompatibility._
import com.klibisz.elastiknn.client.ElastiknnRequests
import com.klibisz.elastiknn.testing.{ElasticAsyncClient, SilentMatchers}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.XContentFactory
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import org.scalatest.{AsyncFunSpec, Inspectors, Matchers, _}

import scala.concurrent.Future
import scala.util.Random
import scala.util.hashing.MurmurHash3

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

  // https://github.com/alexklibisz/elastiknn/issues/97
  describe("Query with filter on another field") {
    implicit val rng: Random = new Random(0)
    val indexPrefix = "issue-97"

    // Generate a corpus of docs. Small number of the them have color blue, rest have color green.
    val dims = 10
    val numTotal = 1000
    val numBlue = 100
    val corpus: Seq[(String, Vec.DenseFloat, String)] =
      (0 until numTotal).map(i => (s"v$i", Vec.DenseFloat.random(dims), if (i < numBlue) "blue" else "green"))

    // Make sure there are fewer candidates than the whole corpus. This ensures the filter is executed before the knn.
    val candidates = numBlue

    // Test with multiple mappings/queries.
    val queryVec = Vec.DenseFloat.random(dims)
    val mappingsAndQueries = Seq(
      Mapping.L2Lsh(dims, 40, 1, 2) -> Seq(
        NearestNeighborsQuery.Exact("vec", Similarity.L2, queryVec),
        NearestNeighborsQuery.Exact("vec", Similarity.Angular, queryVec),
        NearestNeighborsQuery.L2Lsh("vec", candidates, vec = queryVec)
      ),
      Mapping.AngularLsh(dims, 40, 1) -> Seq(
        NearestNeighborsQuery.Exact("vec", Similarity.L2, queryVec),
        NearestNeighborsQuery.Exact("vec", Similarity.Angular, queryVec),
        NearestNeighborsQuery.AngularLsh("vec", candidates, queryVec)
      )
    )

    for {
      (mapping, queries) <- mappingsAndQueries
      query: NearestNeighborsQuery <- queries
      index = s"$indexPrefix-${MurmurHash3.orderedHash(Seq(mapping, query), 0).toString}"
    } it(s"filters with mapping [${mapping}] and query [${query}] in index [$index]") {
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
           |  "size": $numBlue,
           |  "query": {
           |    "term": { "color": "blue" }
           |  },
           |  "rescore": {
           |    "window_size": $candidates,
           |    "query": {
           |      "rescore_query": {
           |        "elastiknn_nearest_neighbors": ${ElasticsearchCodec.nospaces(query)}
           |      },
           |      "query_weight": 0,
           |      "rescore_query_weight": 1
           |    }
           |  }
           |}
           |""".stripMargin
      for {
        _ <- deleteIfExists(index)
        _ <- eknn.createIndex(index)
        _ <- eknn.execute(putMapping(index).rawSource(rawMapping))
        _ <- Future.traverse(corpus.grouped(100)) { batch =>
          val reqs = batch.map {
            case (id, vec, color) =>
              val docSource = s"""{ "vec": ${ElasticsearchCodec.nospaces(vec)}, "color": "$color" }"""
              indexInto(index).id(id).source(docSource)
          }
          eknn.execute(bulk(reqs))
        }
        _ <- eknn.execute(refreshIndex(index))
        res <- eknn.execute(search(index).source(rawQuery))
      } yield {
        res.result.hits.hits.length shouldBe numBlue
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
        _ <- eknn.createIndex(index)
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

  describe("Deleting vectors") {

    // https://github.com/alexklibisz/elastiknn/issues/158
    it("index, search, delete some, search, replace them, search again") {

      implicit val rng: Random = new Random(0)
      val (index, vecField, idField, dims) = ("issue-158", "vec", "id", 100)
      val corpus = Vec.DenseFloat.randoms(dims, 1000)
      val ids = corpus.indices.map(i => s"v$i")
      val mapping = Mapping.L2Lsh(dims, 50, 1, 2)
      val query = NearestNeighborsQuery.L2Lsh(vecField, 30, 1)

      def searchDeleteSearchReplace(): Future[Assertion] = {
        val randomIdx = rng.nextInt(corpus.length)
        val (vec, id) = (corpus(randomIdx), ids(randomIdx))
        for {
          c1 <- eknn.execute(count(index))
          _ = c1.result.count shouldBe corpus.length

          // Search for the randomly-picked vector. It should be its own best match.
          s1 <- eknn.nearestNeighbors(index, query.withVec(vec), 10, idField)
          _ = s1.result.hits.hits.headOption.map(_.id) shouldBe Some(id)

          // Delete the top five vectors.
          deletedIdxs = s1.result.hits.hits.take(5).map(_.id.drop(1).toInt).toSeq
          _ <- Future.traverse(deletedIdxs.map(ids.apply).map(deleteById(index, _)))(eknn.execute(_))
          _ <- eknn.execute(refreshIndex(index))
          c2 <- eknn.execute(count(index))
          _ = c2.result.count shouldBe (corpus.length - deletedIdxs.length)

          // Search again for the original vector. The previous last five results should be the new top five.
          s2 <- eknn.nearestNeighbors(index, query.withVec(vec), 10, idField)
          _ = s2.result.hits.hits.map(_.id).take(5).toSeq shouldBe s1.result.hits.hits.map(_.id).takeRight(5).toSeq

          // Put the deleted vectors back.
          _ <- eknn.index(index, vecField, deletedIdxs.map(corpus.apply), idField, deletedIdxs.map(ids.apply))
          _ <- eknn.execute(refreshIndex(index))
          c3 <- eknn.execute(count(index))
          _ = c3.result.count shouldBe corpus.length

          // Search again for the same original vector.
          s3 <- eknn.nearestNeighbors(index, query.withVec(vec), 10, idField)
          _ = s3.result.hits.hits.map(_.id).sorted shouldBe s1.result.hits.hits.map(_.id).sorted

        } yield Assertions.succeed
      }

      for {
        _ <- deleteIfExists(index)
        _ <- eknn.createIndex(index)
        _ <- eknn.putMapping(index, vecField, idField, mapping)
        _ <- eknn.index(index, vecField, corpus, idField, ids)
        _ <- eknn.execute(refreshIndex(index))

        _ <- searchDeleteSearchReplace()
        _ <- searchDeleteSearchReplace()
        _ <- searchDeleteSearchReplace()
        _ <- searchDeleteSearchReplace()
        _ <- searchDeleteSearchReplace()

      } yield Assertions.succeed
    }
  }

  describe("count vectors using the exists clause") {
    implicit val rng: Random = new Random(0)
    val (index, field, id) = ("issue-174", "vec", "id")
    val corpus = Vec.DenseFloat.randoms(128, 99)
    val ids = corpus.indices.map(i => s"v$i")
    val mappings = Seq(
      Mapping.DenseFloat(corpus.head.dims),
      Mapping.L2Lsh(corpus.head.dims, 50, 1, 2)
    )
    for {
      mapping <- mappings
    } it(s"counts vectors for mapping $mapping") {
      for {
        _ <- deleteIfExists(index)
        _ <- eknn.createIndex(index)
        _ <- eknn.putMapping(index, field, id, mapping)
        _ <- eknn.index(index, field, corpus, id, ids)
        _ <- eknn.execute(refreshIndex(index))
        c1 <- eknn.execute(count(index))
        c2 <- eknn.execute(count(index).query(existsQuery(field)))
      } yield {
        c1.result.count shouldBe corpus.length
        c2.result.count shouldBe corpus.length
      }
    }
  }

  // https://github.com/alexklibisz/elastiknn/issues/176
  describe("Storing, searching multiple vectors in the same doc") {

    it("stores and searches docs with multiple vectors") {
      implicit val rng: Random = new Random(0)

      val index = "issue-176"
      val dims = 10
      val n = 100

      val genDF = () => ElasticsearchCodec.nospaces(Vec.DenseFloat.random(dims))
      val genSB = () => ElasticsearchCodec.nospaces(Vec.SparseBool.random(dims))

      // (Field name, mapping, function to generate a random vector, query to execute)
      // Some of them are intentionally duplicated.
      val fields: Seq[(String, Mapping, () => String, NearestNeighborsQuery)] = Seq(
        ("d1", Mapping.DenseFloat(dims), genDF, NearestNeighborsQuery.Exact("d1", Similarity.L2)),
        ("d2", Mapping.AngularLsh(dims, 10, 1), genDF, NearestNeighborsQuery.AngularLsh("d2", n)),
        ("d3", Mapping.AngularLsh(dims, 10, 1), genDF, NearestNeighborsQuery.AngularLsh("d3", n)),
        ("d4", Mapping.L2Lsh(dims, 21, 2, 3), genDF, NearestNeighborsQuery.L2Lsh("d4", n)),
        ("d5", Mapping.PermutationLsh(dims, 6, false), genDF, NearestNeighborsQuery.PermutationLsh("d5", Similarity.Angular, n)),
        ("b1", Mapping.SparseBool(dims), genSB, NearestNeighborsQuery.Exact("b1", Similarity.Jaccard)),
        ("b2", Mapping.SparseIndexed(dims), genSB, NearestNeighborsQuery.SparseIndexed("b2", Similarity.Jaccard)),
        ("b3", Mapping.JaccardLsh(dims, 10, 2), genSB, NearestNeighborsQuery.JaccardLsh("b3", n)),
        ("b4", Mapping.JaccardLsh(dims, 10, 2), genSB, NearestNeighborsQuery.JaccardLsh("b4", n)),
        ("b5", Mapping.HammingLsh(dims, 10, 3), genSB, NearestNeighborsQuery.HammingLsh("b5", n))
      )

      // Define a mapping with one field for each of the above fields.
      val combinedMapping = {
        val vecFields = fields.map {
          case (name, mapping, _, _) => s""" "$name": ${ElasticsearchCodec.nospaces(mapping)} """
        }
        s"""
             |{
             |  "properties": {
             |    "id": {
             |      "type": "keyword",
             |      "store": true
             |    },
             |    ${vecFields.mkString(",\n")}
             |  }
             |}
             |""".stripMargin
      }

      // Generate docs and index requests for documents that implement this mapping.
      val indexReqs = (0 until n).map { i =>
        val vecFields = fields.map {
          case (name, _, gen, _) => s""""$name":${gen()}"""
        }
        val id = s"v$i"
        val source = s""" { "id": "$id", ${vecFields.mkString(",")} } """
        IndexRequest(index, id = Some(id), source = Some(source))
      }

      // Requests to count the number of docs in which each field exists.
      val countExistsReqs = fields.map(_._1).map(field => count(index).query(existsQuery(field)))

      for {
        _ <- deleteIfExists(index)
        _ <- eknn.createIndex(index)
        _ <- eknn.execute(putMapping(index).rawSource(combinedMapping))
        _ <- eknn.execute(bulk(indexReqs))
        _ <- eknn.execute(refreshIndex(index))
        counts <- Future.sequence(countExistsReqs.map(eknn.execute(_)))
        neighbors <- Future.traverse(fields) {
          case (name, _, _, query) =>
            eknn.nearestNeighbors(index, query.withVec(Vec.Indexed(index, "v0", name)), 5, "id")
        }
      } yield {
        counts.length shouldBe fields.length
        neighbors.length shouldBe fields.length
        forAll(counts.map(_.result.count))(_ shouldBe n)
        forAll(neighbors.map(_.result.hits.hits.head.id))(_ shouldBe "v0")
      }
    }
  }

  describe("Search when not all documents have vectors") {

    // Based on two issues:
    // https://github.com/alexklibisz/elastiknn/issues/181
    // https://github.com/alexklibisz/elastiknn/issues/180
    // This seemed to only cause runtime issues with >= 20k documents.
    // The solution was to ensure the DocIdSetIterator in the MatchHashesAndScoreQuery begins iterating at docID = -1.
    it("Searches docs with a vector mapping, but only half the indexed docs have vectors") {
      implicit val rng = new Random(0)
      val index = "issue-181"
      val (vecField, idField, dims, numDocs) = ("vec", "id", 128, 20000)
      val vecMapping: Mapping = Mapping.AngularLsh(dims, 99, 1)
      val mappingJsonString =
        s"""
           |{
           |  "properties": {
           |    "$vecField": ${ElasticsearchCodec.encode(vecMapping).spaces2},
           |    "$idField": { "type": "keyword" }
           |  }
           |}
           |""".stripMargin

      val v0 = Vec.DenseFloat.random(dims)

      for {
        _ <- deleteIfExists(index)
        _ <- eknn.createIndex(index)
        _ <- eknn.execute(putMapping(index).rawSource(mappingJsonString))
        _ <- Future.traverse((0 until numDocs).grouped(500)) { ids =>
          val reqs = ids.map { i =>
            if (i % 2 == 0) ElastiknnRequests.index(index, vecField, if (i == 0) v0 else Vec.DenseFloat.random(dims), idField, s"v$i")
            else indexInto(index).id(s"v$i").fields(Map(idField -> s"v$i"))
          }
          eknn.execute(bulk(reqs))
        }
        _ <- eknn.execute(refreshIndex(index))

        countWithIdField <- eknn.execute(count(index).query(existsQuery(idField)))
        countWithVecField <- eknn.execute(count(index).query(existsQuery(vecField)))
        _ = countWithIdField.result.count shouldBe numDocs
        _ = countWithVecField.result.count shouldBe numDocs / 2

        nbrsExact <- eknn.nearestNeighbors(index, NearestNeighborsQuery.Exact(vecField, Similarity.Angular, v0), 10, idField)
        _ = nbrsExact.result.hits.hits.length shouldBe 10
        _ = nbrsExact.result.hits.hits.head.score shouldBe 2f

        nbrsApprox <- eknn.nearestNeighbors(index, NearestNeighborsQuery.AngularLsh(vecField, 13, v0), 10, idField)
        _ = nbrsApprox.result.hits.hits.length shouldBe 10
        _ = nbrsApprox.result.hits.hits.head.score shouldBe 2f
      } yield Assertions.succeed
    }
  }

}
