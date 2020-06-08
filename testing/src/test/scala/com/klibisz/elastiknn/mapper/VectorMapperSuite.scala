package com.klibisz.elastiknn.mapper

import java.util.UUID

import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping, Vec}
import com.klibisz.elastiknn.testing.{Elastic4sMatchers, ElasticAsyncClient}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{Indexes, Response}
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.get.GetResponse
import io.circe.{Json, JsonObject}
import org.scalatest._
import io.circe.parser.parse

import scala.concurrent.Future
import scala.util.Random

class VectorMapperSuite extends AsyncFunSuite with Matchers with Inspectors with Elastic4sMatchers with ElasticAsyncClient {

  implicit val rng: Random = new Random(0)

  test("create index and put mapping") {
    val indexName = s"test-${UUID.randomUUID()}"
    val mappings: Seq[(String, Mapping)] = Seq(
      ("vec_spv", Mapping.SparseBool(100)),
      ("vec_dfv", Mapping.DenseFloat(100)),
      ("vec_spix", Mapping.SparseIndexed(100)),
      ("vec_jcdlsh", Mapping.JaccardLsh(100, 65, 1))
    )
    for {
      createIndexRes <- eknn.execute(createIndex(indexName))
      _ = createIndexRes.shouldBeSuccess

      putMappingReqs = mappings.map {
        case (fieldName, mapping) => eknn.putMapping(indexName, fieldName, mapping)
      }
      _ <- Future.sequence(putMappingReqs)

      getMappingReqs = mappings.map {
        case (fieldName, _) => eknn.execute(getMapping(Indexes(indexName), fieldName))
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
            x <- x.findAllByKey(indexName).headOption
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
    val fieldName = "vec"
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
            _ <- eknn.execute(createIndex(indexName))
            _ <- eknn.putMapping(indexName, fieldName, mapping)
          } yield ()
      }
      _ <- Future.sequence(putMappingReqs)

      indexReqs = inputs.map {
        case (indexName, _, vecs, ids) => eknn.index(indexName, fieldName, vecs, Some(ids), refresh = RefreshPolicy.IMMEDIATE)
      }
      _ <- Future.sequence(indexReqs)

      getReqs = inputs.map {
        case (indexName, _, _, ids) =>
          Future.sequence(ids.map(id => eknn.execute(get(indexName, id).fetchSourceInclude(fieldName))))
      }

      getResponses: Seq[Seq[Response[GetResponse]]] <- Future.sequence(getReqs)

    } yield
      forAll(inputs.zip(getResponses)) {
        case ((_, _, vectors, _), getResponses) =>
          getResponses should have length vectors.length
          val parsedVectors = getResponses.map(_.result.sourceAsString).map(parse)
          forAll(parsedVectors)(_ shouldBe 'right)
          val encodedVectors = vectors.map(v => Json.fromJsonObject(JsonObject(fieldName -> ElasticsearchCodec.encode(v))))
          forAll(encodedVectors)(v => parsedVectors should contain(Right(v)))
      }
  }

  test("throw an error given vector with bad dimensions") {
    val indexName = s"test-intentional-failure-${UUID.randomUUID()}"
    val dims = 100
    val inputs = Seq(
      ("intentional-failure-sbv", Mapping.SparseBool(dims), Vec.SparseBool.random(dims + 1)),
      ("intentional-failure-dfv", Mapping.DenseFloat(dims), Vec.DenseFloat.random(dims + 1))
    )
    for {
      _ <- eknn.execute(createIndex(indexName))
      putMappingReqs = inputs.map {
        case (fieldName, mapping, _) => eknn.putMapping(indexName, fieldName, mapping)
      }
      _ <- Future.sequence(putMappingReqs)

      indexReqs = inputs.map {
        case (fieldName, _, vec) =>
          recoverToExceptionIf[RuntimeException] {
            eknn.index(indexName, fieldName, Seq(vec), refresh = RefreshPolicy.IMMEDIATE)
          }
      }
      exceptions <- Future.sequence(indexReqs)

    } yield forAll(exceptions)(_.getMessage shouldBe "mapper_parsing_exception failed to parse")
  }

}
