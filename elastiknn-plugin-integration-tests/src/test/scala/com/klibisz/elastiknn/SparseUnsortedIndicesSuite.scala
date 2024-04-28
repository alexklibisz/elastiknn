package com.klibisz.elastiknn

import com.klibisz.elastiknn.api._
import com.sksamuel.elastic4s.ElasticDsl._
import org.scalatest.Inspectors
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.util.Random

class SparseUnsortedIndicesSuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient {

  // https://gitter.im/elastiknn/community?at=5f3012df65e829425e70ee31

  given rng: Random = new Random(0)
  val indexPrefix = "test-sbv-unsorted"

  val dims = 20000
  val corpus: Vector[Vec.SparseBool] = Vec.SparseBool.randoms(dims, 100)

  val queryVec: Vec.SparseBool = {
    val sorted = corpus.head
    val shuffled = rng.shuffle(sorted.trueIndices.toVector).toArray
    sorted.copy(shuffled)
  }

  // Test with multiple mappings/queries.
  val mappingsAndQueries: Seq[(Mapping, Seq[NearestNeighborsQuery])] = Seq(
    Mapping.SparseBool(dims) -> Seq(
      NearestNeighborsQuery.Exact("vec", Similarity.Jaccard, queryVec),
      NearestNeighborsQuery.Exact("vec", Similarity.Hamming, queryVec)
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
  } test(s"finds unsorted sparse bool vecs with mapping [${mapping}] and query [${query}]") {
    val index = s"$indexPrefix-${UUID.randomUUID.toString}"
    for {
      _ <- deleteIfExists(index)
      _ <- eknn.createIndex(index)
      _ <- eknn.putMapping(index, "vec", "id", mapping)
      _ <- eknn.execute(refreshIndex(index))
      _ <- eknn.index(index, "vec", corpus, "id", corpus.indices.map(i => s"v$i"))
      _ <- eknn.execute(refreshIndex(index))
      res <- eknn.nearestNeighbors(index, query, 5, "id")
    } yield {
      res.result.maxScore shouldBe 1d
      res.result.hits.hits.head.id shouldBe "v0"
    }
  }

}
