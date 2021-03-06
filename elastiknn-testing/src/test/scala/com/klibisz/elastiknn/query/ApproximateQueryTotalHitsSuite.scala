package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.testing.ElasticAsyncClient
import com.sksamuel.elastic4s.ElasticDsl._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.util.Random

class ApproximateQueryTotalHitsSuite extends AsyncFunSuite with Matchers with ElasticAsyncClient {

  test("same approximate query should return same total hits") {

    implicit val rng: Random = new Random(0)
    val (index, vecField, idField, dims) = ("issue-240", "vec", "id", 80)
    val corpus = Vec.DenseFloat.randoms(dims, 9999)
    val ids = corpus.indices.map(i => s"v$i")
    val mapping = Mapping.AngularLsh(dims, 75, 2)
    val query = NearestNeighborsQuery.AngularLsh(vecField, 30)
    val k = 42

    // Run search for the first n vectors, in shuffled order, and return the index, the number of hits, and the hit IDs.
    def search(n: Int = 100): Future[Seq[(Int, Long, Vector[String])]] = {
      val queriesWithIndices = corpus.take(n).map(query.withVec).zipWithIndex
      val shuffled = Random.shuffle(queriesWithIndices)
      val running = shuffled.map { case (q, i) =>
        eknn.nearestNeighbors(index, q, k, idField).map(r => (i, r.result.totalHits, r.result.hits.hits.toVector.map(_.id)))
      }
      Future.sequence(running).map(_.sortBy(_._1))
    }

    for {
      _ <- deleteIfExists(index)
      _ <- eknn.createIndex(index, 3, 2)
      _ <- eknn.putMapping(index, vecField, idField, mapping)
      _ <- eknn.index(index, vecField, corpus, idField, ids)
      _ <- eknn.execute(refreshIndex(index))

      s1: Seq[(Int, Long, Vector[String])] <- search()
      s2: Seq[(Int, Long, Vector[String])] <- search()
      s3: Seq[(Int, Long, Vector[String])] <- search()
    } yield {
      s1 shouldBe s2
      s2 shouldBe s3
    }
  }


}
