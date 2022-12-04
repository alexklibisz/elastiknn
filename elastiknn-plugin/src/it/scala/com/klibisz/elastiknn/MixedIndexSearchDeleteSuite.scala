package com.klibisz.elastiknn

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn._
import com.sksamuel.elastic4s.ElasticDsl._
import futil.Futil
import org.scalatest._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.util.Random

class MixedIndexSearchDeleteSuite extends AsyncFunSuite with Matchers with Inspectors with ElasticAsyncClient with SilentMatchers {

  // https://github.com/alexklibisz/elastiknn/issues/158
  test("index, search, delete some, search, replace them, search again") {

    implicit val rng: Random = new Random(0)
    val (index, vecField, idField, dims) = ("issue-158", "vec", "id", 100)
    val corpus = Vec.DenseFloat.randoms(dims, 1000)
    val ids = corpus.indices.map(i => s"v$i")
    val mapping = Mapping.L2Lsh(dims, 50, 1, 2)
    val query = NearestNeighborsQuery.L2Lsh(vecField, 100, 1)
    val k = 42

    def searchDeleteSearchReplace(): Future[Assertion] = {
      val randomIdx = rng.nextInt(corpus.length)
      val (vec, id) = (corpus(randomIdx), ids(randomIdx))
      for {
        c1 <- eknn.execute(count(index))
        _ = c1.result.count shouldBe corpus.length

        // Search for the randomly-picked vector. It should be its own best match.
        s1 <- eknn.nearestNeighbors(index, query.withVec(vec), k, idField)
        _ = s1.result.hits.hits.headOption.map(_.id) shouldBe Some(id)

        // Delete the top five vectors.
        deletedIdxs = s1.result.hits.hits.take(5).map(_.id.drop(1).toInt).toSeq
        _ <- Futil.traverseParN(8)(deletedIdxs.map(ids.apply).map(deleteById(index, _)))(eknn.execute(_))
        _ <- eknn.execute(refreshIndex(index))
        c2 <- eknn.execute(count(index))
        _ = c2.result.count shouldBe (corpus.length - deletedIdxs.length)

        // Search again for the original vector. The previous last five results should be the new top five.
        s2 <- eknn.nearestNeighbors(index, query.withVec(vec), k, idField)
        _ = s2.result.hits.hits.map(_.id).take(5).toSeq shouldBe s1.result.hits.hits.map(_.id).slice(5, 10).toSeq
        _ = s2.result.totalHits shouldBe s1.result.totalHits - 5

        // Put the deleted vectors back.
        _ <- eknn.index(index, vecField, deletedIdxs.map(corpus.apply), idField, deletedIdxs.map(ids.apply))
        _ <- eknn.execute(refreshIndex(index))
        c3 <- eknn.execute(count(index))
        _ = c3.result.count shouldBe corpus.length

        // Search again for the same original vector.
        s3 <- eknn.nearestNeighbors(index, query.withVec(vec), k, idField)
        _ = s3.result.hits.hits.map(_.id).sorted shouldBe s1.result.hits.hits.map(_.id).sorted
        _ = s3.result.totalHits shouldBe s1.result.totalHits

      } yield Assertions.succeed
    }

    for {
      _ <- deleteIfExists(index)
      _ <- eknn.createIndex(index, 3)
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
