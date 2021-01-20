package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.{ElasticAsyncClient, SilentMatchers}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import org.scalatest.Inspectors
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.util.Random

class DocsWithMultipleVectorsSuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

  // https://github.com/alexklibisz/elastiknn/issues/176
  test("storing and searching docs with multiple vectors") {

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
