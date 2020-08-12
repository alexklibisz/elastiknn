package com.klibisz.elastiknn.benchmarks

import java.net.URI
import java.util.concurrent.TimeUnit

import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Vec}
import com.klibisz.elastiknn.benchmarks.ElastiknnZioClient.Service
import com.klibisz.elastiknn.client.{ElastiknnClient, ElastiknnRequests}
import com.sksamuel.elastic4s.{ElasticClient, Handler, Response}
import org.apache.lucene.codecs.Codec
import zio._
import zio.stream._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.cluster.ClusterHealthResponse
import com.sksamuel.elastic4s.requests.common.HealthStatus
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.Duration
import zio.logging._

import scala.concurrent.Future

trait SearchClient {
  def blockUntilReady(): Task[Unit]

  def indexExists(index: String): Task[Boolean]

  def buildIndex(index: String, field: String, mapping: Mapping, shards: Int, vectors: Stream[Throwable, Vec]): Task[Unit]

  def search(queries: Stream[Throwable, NearestNeighborsQuery], parallelism: Int): Stream[Throwable, QueryResult]

  def close(): Task[Unit]
}

object SearchClient {

  def elasticsearch(uri: URI, strictFailure: Boolean, timeoutMillis: Int): ZLayer[Has[Logger[String]], Throwable, Has[SearchClient]] = {

    ZLayer.fromServiceM[Logger[String], Any, Throwable, SearchClient] { log =>
      ZIO.fromFuture { implicit ec =>
        Future {
          new SearchClient {

            private val client = ElastiknnClient.futureClient(uri.getHost, uri.getPort, strictFailure, timeoutMillis)

            private def execute[T, U](t: T)(implicit handler: Handler[T, U], manifest: Manifest[U]): Task[Response[U]] =
              Task.fromFuture(_ => client.execute(t))

            override def blockUntilReady(): Task[Unit] = {
              val check = clusterHealth.waitForStatus(HealthStatus.Yellow).timeout("90s")
              val sched = Schedule.recurs(10) && Schedule.spaced(Duration(10, TimeUnit.SECONDS))
              execute(check).retry(sched).map(_ => ()).provideLayer(Clock.live)
            }

            override def indexExists(index: String): Task[Boolean] =
              execute(ElasticDsl.indexExists(index)).map(_.result.exists).catchSome {
                case _: ElastiknnClient.StrictFailureException => ZIO.succeed(false)
              }

            override def buildIndex(index: String,
                                    field: String,
                                    mapping: Mapping,
                                    shards: Int,
                                    vectors: Stream[Throwable, Vec]): Task[Unit] = {
              for {
                _ <- log.info(s"Creating index [$index] with [0] replicas and [$shards] shards")
                _ <- execute(createIndex(index).replicas(0).shards(shards).indexSetting("refresh_interval", "-1"))
                _ <- execute(ElastiknnRequests.putMapping(index, field, "id", mapping))
                _ <- vectors.grouped(200).zipWithIndex.foreach {
                  case (vecs, batchIndex) =>
                    val ids = vecs.indices.map(i => s"$batchIndex-$i")
                    ???

                }
              } yield ()

              ???
            }

            override def search(queries: Stream[Throwable, NearestNeighborsQuery], parallelism: Int): Stream[Throwable, QueryResult] = ???

            override def close(): Task[Unit] = ???
          }
        }
      }
    }

  }

  def luceneInMemory(): Layer[Throwable, Has[SearchClient]] =
    ZLayer.succeed {
      new SearchClient {
        override def blockUntilReady(): Task[Unit] = ???
        override def indexExists(index: String): Task[Boolean] = ???
        override def buildIndex(index: String, field: String, mapping: Mapping, shards: Int, vectors: Stream[Throwable, Vec]): Task[Unit] =
          ???
        def search(queries: Stream[Throwable, NearestNeighborsQuery], parallelism: Int): Stream[Throwable, QueryResult] = ???
        override def close(): Task[Unit] = ???
      }
    }

}
