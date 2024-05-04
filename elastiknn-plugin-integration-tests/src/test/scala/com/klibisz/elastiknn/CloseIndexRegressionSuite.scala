package com.klibisz.elastiknn

import com.klibisz.elastiknn.api.Vec
import com.sksamuel.elastic4s.ElasticDsl._
import org.scalatest.Assertions
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class CloseIndexRegressionSuite extends AsyncFunSuite with Matchers with ElasticAsyncClient {

  given rng: Random = new Random(0)

  test("close index without elastiknn setting") {
    val index = "issue-215-close-no-elastiknn"
    val corpus = Vec.DenseFloat.randoms(42, 10000)
    for {
      _ <- deleteIfExists(index)
      _ <- eknn.execute(createIndex(index).shards(1).indexSetting("elastiknn", false))
      _ <- eknn.index(index, "vec", corpus, "id", corpus.indices.map(_ + 1).map(_.toString))
      _ <- eknn.execute(refreshIndex(index))
      _ <- eknn.execute(forceMerge(index).maxSegments(1))
      _ <- eknn.execute(closeIndex(index))
      _ <- deleteIfExists(index)
    } yield Assertions.succeed
  }
}
