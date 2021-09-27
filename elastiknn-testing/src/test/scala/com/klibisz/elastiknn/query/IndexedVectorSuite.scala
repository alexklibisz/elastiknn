package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.ElasticAsyncClient
import com.sksamuel.elastic4s.ElasticDsl._
import org.scalatest.Succeeded
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class IndexedVectorSuite extends AsyncFreeSpec with Matchers with ElasticAsyncClient {

  "Issue 310" - {
    "Informative errors when indexed vector index, doc, or field do not exist" in {
      implicit val rng: Random = new Random(0)
      val (indexName, vecFieldName, idFieldName) = ("issue-310", "vec", "id")
      val corpus = Vec.DenseFloat.randoms(42, 99)
      val ids = corpus.indices.map(i => s"v$i")
      val mapping = Mapping.DenseFloat(corpus.head.dims)
      for {
        _ <- deleteIfExists(indexName)
        _ <- eknn.createIndex(indexName)
        _ <- eknn.putMapping(indexName, vecFieldName, idFieldName, mapping)
        _ <- eknn.index(indexName, vecFieldName, corpus, idFieldName, ids)
        _ <- eknn.execute(refreshIndex(indexName))

        // Search using a valid index, id, and field.
        id1 = ids.head
        nnq1 = NearestNeighborsQuery.Exact(vecFieldName, Similarity.L2, Vec.Indexed(indexName, id1, vecFieldName))
        res1 <- eknn.nearestNeighbors(indexName, nnq1, 10, idFieldName)
        _ = res1.result.hits.size shouldBe 10
        _ = res1.result.hits.hits.head.id shouldBe id1

        // Search using an id that doesn't exist.
        id2 = s"wrong-${rng.nextInt()}"
        nnq2 = NearestNeighborsQuery.Exact(vecFieldName, Similarity.L2, Vec.Indexed(indexName, id2, vecFieldName))
        res2 <- recoverToExceptionIf[Exception](eknn.nearestNeighbors(indexName, nnq2, 10, idFieldName))
        _ = res2.getMessage shouldBe s"resource_not_found_exception Document with id [$id2] in index [$indexName] not found"

        // Search using an index that doesn't exist.
        id3 = ids.head
        index3 = s"wrong-${rng.nextInt()}"
        nnq3 = NearestNeighborsQuery.Exact(vecFieldName, Similarity.L2, Vec.Indexed(index3, id3, vecFieldName))
        res3 <- recoverToExceptionIf[Exception](eknn.nearestNeighbors(indexName, nnq3, 10, idFieldName))
        _ = res3.getMessage shouldBe s"index_not_found_exception no such index [$index3]"

        // Search using a field that doesn't exist.
        id4 = ids.head
        field4 = s"wrong-${rng.nextInt()}"
        nnq4 = NearestNeighborsQuery.Exact(vecFieldName, Similarity.L2, Vec.Indexed(indexName, id4, field4))
        res4 <- recoverToExceptionIf[Exception](eknn.nearestNeighbors(indexName, nnq4, 10, idFieldName))
        _ = res4.getMessage shouldBe s"resource_not_found_exception Document with id [$id4] in index [$indexName] exists, but does not have field [$field4]"
      } yield Succeeded
    }
  }

}
