package com.klibisz.elastiknn.mapper

import java.util.UUID

import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping, NearestNeighborsQuery, Vec}
import com.klibisz.elastiknn.testing.{Elastic4sMatchers, ElasticAsyncClient}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.{Indexes, Response}
import io.circe.parser.parse
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.scalatest._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.util.Random

class VectorMapperSuite extends AsyncFunSuite with Matchers with Inspectors with Elastic4sMatchers with ElasticAsyncClient {

  implicit val rng: Random = new Random(0)

  test("create index and put mapping") {
    val index = s"test-${UUID.randomUUID()}"
    val storedIdField = "id"
    val mappings: Seq[(String, Mapping)] = Seq(
      ("vec_spv", Mapping.SparseBool(100)),
      ("vec_dfv", Mapping.DenseFloat(100)),
      ("vec_spix", Mapping.SparseIndexed(100)),
      ("vec_jcdlsh", Mapping.JaccardLsh(100, 65, 1))
    )
    for {
      createIndexRes <- eknn.createIndex(index)
      _ = createIndexRes.shouldBeSuccess

      putMappingReqs = mappings.map {
        case (vecField, mapping) => eknn.putMapping(index, vecField, storedIdField, mapping)
      }
      _ <- Future.sequence(putMappingReqs)

      getMappingReqs = mappings.map {
        case (fieldName, _) => eknn.execute(getMapping(Indexes(index), fieldName))
      }
      getMappingRes <- Future.sequence(getMappingReqs)
    } yield
      forAll(mappings.zip(getMappingRes)) {
        case ((fieldName, mapping), res) =>
          // Just check the JSON directly. Example structure:
          // {
          //  "test-226cf173-38d9-40e3-8c3d-3aabccd182ae": {
          //    "mappings": {
          //      "vec_spv": {
          //        "full_name": "vec_spv",
          //        "mapping": {
          //          "vec_spv": {
          //            "type": "elastiknn_sparse_bool_vector",
          //            "elastiknn": {
          //              "dims": 100
          //            }
          //          }
          //        }
          //      }
          //    }
          //  }
          //}

          res.body shouldBe defined
          val json = parse(res.body.get)
          json shouldBe 'right

          val encoded = ElasticsearchCodec.encode(mapping)

          val mappingJsonOpt: Option[JsonObject] = for {
            x <- json.toOption
            x <- x.findAllByKey(index).headOption
            x <- x.findAllByKey("mappings").headOption
            x <- x.findAllByKey(fieldName).headOption
            x <- x.findAllByKey("mapping").headOption
            x <- x.findAllByKey(fieldName).headOption
            x <- x.asObject
            y <- encoded.asObject
            // The returned mapping might contain some more items, like similarity, so filter them out.
          } yield x.filterKeys(y.keys.toSet.contains)

          mappingJsonOpt shouldBe encoded.asObject
      }

  }

  test("store and read vectors") {
    val vecField = "vec"
    val storedIdField = "id"
    val (dims, n) = (100, 10)
    def ids: Seq[String] = (0 until n).map(_ => UUID.randomUUID().toString)
    val inputs: Seq[(String, Mapping, Vector[Vec], Seq[String])] = Seq(
      // (index, mapping, random vectors, vector ids
      (s"test-${UUID.randomUUID()}", Mapping.SparseBool(dims), Vec.SparseBool.randoms(dims, n), ids),
      (s"test-${UUID.randomUUID()}", Mapping.DenseFloat(dims), Vec.DenseFloat.randoms(dims, n), ids),
      (s"test-${UUID.randomUUID()}", Mapping.SparseIndexed(dims), Vec.SparseBool.randoms(dims, n), ids),
      (s"test-${UUID.randomUUID()}", Mapping.JaccardLsh(dims, 65, 1), Vec.SparseBool.randoms(dims, n), ids)
    )

    for {
      _ <- Future.successful(())

      putMappingReqs = inputs.map {
        case (indexName, mapping, _, _) =>
          for {
            _ <- eknn.createIndex(indexName)
            _ <- eknn.putMapping(indexName, vecField, storedIdField, mapping)
          } yield ()
      }
      _ <- Future.sequence(putMappingReqs)

      indexReqs = inputs.map {
        case (indexName, _, vecs, ids) => eknn.index(indexName, vecField, vecs, storedIdField, ids)
      }
      _ <- Future.sequence(indexReqs)
      _ <- eknn.execute(refreshIndex(inputs.map(_._1)))

      getReqs = inputs.map {
        case (indexName, _, _, ids) =>
          Future.sequence(ids.map(id => eknn.execute(get(indexName, id).fetchSourceInclude(vecField))))
      }

      getResponses: Seq[Seq[Response[GetResponse]]] <- Future.sequence(getReqs)

    } yield
      forAll(inputs.zip(getResponses)) {
        case ((_, _, vectors, _), getResponses) =>
          getResponses should have length vectors.length
          val parsedVectors = getResponses.map(_.result.sourceAsString).map(parse)
          forAll(parsedVectors)(_ shouldBe 'right)
          val encodedVectors = vectors.map(v => Json.fromJsonObject(JsonObject(vecField -> ElasticsearchCodec.encode(v))))
          forAll(encodedVectors)(v => parsedVectors should contain(Right(v)))
      }
  }

  test("throw an error given vector with bad dimensions") {
    val index = s"test-intentional-failure-${UUID.randomUUID()}"
    val storedIdField = "id"
    val dims = 100
    val inputs = Seq(
      ("intentional-failure-sbv", Mapping.SparseBool(dims), Vec.SparseBool.random(dims + 1)),
      ("intentional-failure-dfv", Mapping.DenseFloat(dims), Vec.DenseFloat.random(dims + 1))
    )
    for {
      _ <- eknn.createIndex(index)
      putMappingReqs = inputs.map {
        case (fieldName, mapping, _) => eknn.putMapping(index, fieldName, storedIdField, mapping)
      }
      _ <- Future.sequence(putMappingReqs)

      indexReqs = inputs.map {
        case (fieldName, _, vec) =>
          recoverToExceptionIf[RuntimeException] {
            eknn.index(index, fieldName, Seq(vec), storedIdField, Seq(UUID.randomUUID().toString))
          }
      }
      exceptions <- Future.sequence(indexReqs)

    } yield forAll(exceptions)(_.getMessage shouldBe "mapper_parsing_exception failed to parse")
  }

  // https://github.com/alexklibisz/elastiknn/issues/177
  test("index shorthand dense float vectors") {
    val (index, dims, vecField, idField) = ("issue-177-dense", 42, "vec", "id")
    val corpus = Vec.DenseFloat.randoms(dims, 1000)
    val mapping = Mapping.L2Lsh(dims, 33, 1, 1)
    val ixReqs = corpus.zipWithIndex.map {
      case (vec, i) =>
        val source = s""" { "$idField": "v$i", "$vecField": ${vec.values.asJson.noSpaces} } """
        IndexRequest(index, source = Some(source))
    }
    for {
      _ <- deleteIfExists(index)
      _ <- eknn.createIndex(index)
      _ <- eknn.putMapping(index, vecField, idField, mapping)
      _ <- eknn.execute(bulk(ixReqs))
      _ <- eknn.execute(refreshIndex(index))
      count <- eknn.execute(count(index).query(existsQuery(vecField)))
      nbrs <- eknn.nearestNeighbors(index, NearestNeighborsQuery.L2Lsh(vecField, 10, 1, corpus.head), 10, idField)
    } yield {
      count.result.count shouldBe corpus.length
      nbrs.result.hits.hits.length shouldBe 10
      nbrs.result.hits.hits.head.id shouldBe "v0"
    }
  }

  // https://github.com/alexklibisz/elastiknn/issues/177
  test("index shorthand sparse bool vectors") {
    val (index, dims, vecField, idField) = ("issue-177-sparse", 42, "vec", "id")
    val corpus = Vec.SparseBool.randoms(dims, 1000)
    val mapping = Mapping.JaccardLsh(dims, 20, 1)
    val ixReqs = corpus.zipWithIndex.map {
      case (vec, i) =>
        val source = s""" { "$idField": "v$i", "$vecField": [${vec.trueIndices.asJson.noSpaces}, ${vec.totalIndices}] } """
        IndexRequest(index, source = Some(source))
    }
    for {
      _ <- deleteIfExists(index)
      _ <- eknn.createIndex(index)
      _ <- eknn.putMapping(index, vecField, idField, mapping)
      _ <- eknn.execute(bulk(ixReqs))
      _ <- eknn.execute(refreshIndex(index))
      count <- eknn.execute(count(index).query(existsQuery(vecField)))
      nbrs <- eknn.nearestNeighbors(index, NearestNeighborsQuery.JaccardLsh(vecField, 10, corpus.head), 10, idField)
    } yield {
      count.result.count shouldBe corpus.length
      nbrs.result.hits.hits.length shouldBe 10
      nbrs.result.hits.hits.head.id shouldBe "v0"
    }
  }

}
