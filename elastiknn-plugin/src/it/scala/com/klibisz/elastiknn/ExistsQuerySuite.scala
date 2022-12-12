package com.klibisz.elastiknn

import com.klibisz.elastiknn.api._
import com.sksamuel.elastic4s.ElasticDsl._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class ExistsQuerySuite extends AsyncFunSuite with Matchers with ElasticAsyncClient with SilentMatchers {

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
  } test(s"counts vectors for mapping $mapping") {
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
