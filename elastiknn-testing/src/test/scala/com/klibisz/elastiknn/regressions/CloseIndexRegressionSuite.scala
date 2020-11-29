package com.klibisz.elastiknn.regressions

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.testing.ElasticAsyncClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.indexes.admin.CloseIndexResponse
import com.sksamuel.elastic4s.{ElasticRequest, Handler}
import org.scalatest.Assertions
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class CloseIndexRegressionSuite extends AsyncFunSuite with Matchers with ElasticAsyncClient {

  implicit val rng = new Random(0)

  case class FreezeRequest(index: String)
  implicit object FreezeHandler extends Handler[FreezeRequest, CloseIndexResponse] {
    override def build(t: FreezeRequest): ElasticRequest = ElasticRequest("POST", s"/${t.index}/_freeze")
  }

  def freezeIndex(index: String): FreezeRequest = FreezeRequest(index)

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
    } yield Assertions.succeed
  }

  test("close index with elastiknn setting") {
    val index = "issue-215-close-elastiknn"
    val corpus = Vec.DenseFloat.randoms(42, 10000)
    for {
      _ <- deleteIfExists(index)
      _ <- eknn.execute(createIndex(index).shards(1).indexSetting("elastiknn", true))
      _ <- eknn.index(index, "vec", corpus, "id", corpus.indices.map(_ + 1).map(_.toString))
      _ <- eknn.execute(refreshIndex(index))
      _ <- eknn.execute(forceMerge(index).maxSegments(1))
      _ <- eknn.execute(closeIndex(index))
    } yield Assertions.succeed
  }

  test("freeze index without elastiknn setting") {
    val index = "issue-215-freeze-no-elastiknn"
    val corpus = Vec.DenseFloat.randoms(42, 10000)
    for {
      _ <- deleteIfExists(index)
      _ <- eknn.execute(createIndex(index).shards(1).indexSetting("elastiknn", false))
      _ <- eknn.index(index, "vec", corpus, "id", corpus.indices.map(_ + 1).map(_.toString))
      _ <- eknn.execute(refreshIndex(index))
      _ <- eknn.execute(forceMerge(index).maxSegments(1))
      _ <- eknn.execute(freezeIndex(index))
    } yield Assertions.succeed
  }

  ignore("freeze index with elastiknn setting") {
    val index = "issue-215-freeze-elastiknn"
    val corpus = Vec.DenseFloat.randoms(42, 10000)
    for {
      _ <- deleteIfExists(index)
      _ <- eknn.execute(createIndex(index).shards(1).indexSetting("elastiknn", true))
      _ <- eknn.index(index, "vec", corpus, "id", corpus.indices.map(_ + 1).map(_.toString))
      _ <- eknn.execute(refreshIndex(index))
      _ <- eknn.execute(forceMerge(index).maxSegments(1))
      _ <- eknn.execute(freezeIndex(index))
    } yield Assertions.succeed
  }

}
