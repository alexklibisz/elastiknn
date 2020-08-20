package com.klibisz.elastiknn.server

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec.DenseFloat
import com.klibisz.elastiknn.testing.ElasticAsyncClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.HealthStatus
import org.apache.http.HttpHost
import org.scalatest.{Assertions, AsyncFunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class ServerSuite extends AsyncFunSuite with Matchers with ElasticAsyncClient {

  override lazy val httpHost: HttpHost = new HttpHost("localhost", 8080)

  private implicit val sys: ActorSystem = ActorSystem("elastiknn-server")
  private val server = Http().newServerAt("localhost", 8080)
  Await.result(server.bind(Server.elasticRoutes(new FakeElasticsearch, Logging.InfoLevel)), 1.seconds)

  private implicit val rng = new Random(0)

  private val deleteAll = deleteIndex("_all")

  test("end-to-end client usage") {
    for {
      // Wait for ready.
      _ <- eknn.execute(clusterHealth().waitForStatus(HealthStatus.Red))
      _ <- eknn.execute(clusterHealth().waitForStatus(HealthStatus.Yellow))
      _ <- eknn.execute(clusterHealth().waitForStatus(HealthStatus.Green))

      // Reset cluster state.
      _ <- eknn.execute(deleteAll)

      // Create a couple indices, no mappings.
      _ <- eknn.execute(createIndex("foo").shards(1).replicas(0))
      _ <- eknn.execute(createIndex("bar"))

      // Fails to re-create an existing index.
      _ <- recoverToExceptionIf[RuntimeException](eknn.execute(createIndex("foo")))

      // Deletes and re-creates specific indices.
      _ <- eknn.execute(deleteIndex("foo", "bar"))
      _ <- eknn.execute(createIndex("foo"))
      _ <- eknn.execute(createIndex("bar"))
      _ <- eknn.execute(deleteAll)

      // Creates index, puts mapping, fails to put mapping after it already exists and if it doesn't use "vec" field.
      _ <- eknn.execute(createIndex("foo"))
      _ <- eknn.putMapping("foo", "vec", "id", Mapping.DenseFloat(10))
      _ <- recoverToExceptionIf[RuntimeException](eknn.putMapping("foo", "vec", "id", Mapping.SparseBool(99)))
      _ <- eknn.execute(createIndex("bar"))
      _ <- recoverToExceptionIf[RuntimeException](eknn.putMapping("bar", "notvec", "id", Mapping.DenseFloat(10)))
      _ <- eknn.execute(deleteAll)

      // Index and search vectors.
      _ <- eknn.execute(createIndex("foo"))
      _ <- eknn.putMapping("foo", "vec", "id", Mapping.DenseFloat(10))
      corpus = Vec.DenseFloat.randoms(10, 1000)
      ids = corpus.indices.map(i => s"v$i")
      _ <- eknn.index("foo", "vec", corpus, "id", ids)

    } yield Assertions.succeed
  }

}
