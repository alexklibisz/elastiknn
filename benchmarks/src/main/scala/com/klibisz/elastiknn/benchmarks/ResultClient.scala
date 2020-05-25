package com.klibisz.elastiknn.benchmarks

import com.amazonaws.services.s3.AmazonS3
import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery}
import io.circe.parser.decode
import io.circe.syntax._
import zio._
import codecs._
import zio.blocking._

import scala.util.hashing.MurmurHash3

object ResultClient {

  trait Service {
    def find(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): IO[Throwable, Option[Result]]
    def save(result: Result): IO[Throwable, Unit]
  }

  def s3(bucket: String, keyPrefix: String): ZLayer[Has[AmazonS3] with Blocking, Nothing, ResultClient] = {

    def genKey(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): String = {
      val suffix = s"results-${MurmurHash3.orderedHash(Seq(dataset, mapping, query, k))}.json"
      if (keyPrefix.nonEmpty) s"$keyPrefix/$suffix".replace("//", "/")
      else suffix
    }

    ZLayer.fromServices[AmazonS3, Blocking.Service, Service] {
      case (client, blocking) =>
        new Service {
          override def find(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): IO[Throwable, Option[Result]] = {
            val key = genKey(dataset, mapping, query, k)
            for {
              ex <- blocking.effectBlocking(client.doesObjectExist(bucket, key))
              res: Option[Result] <- if (ex) {
                for {
                  body <- blocking.effectBlocking(client.getObjectAsString(bucket, key))
                  dec <- ZIO.fromEither(decode[Result](body))
                } yield Some(dec)
              } else ZIO.effectTotal(None)
            } yield res
          }

          override def save(result: Result): IO[Throwable, Unit] = {
            val key = genKey(result.dataset, result.mapping, result.query, result.k)
            for {
              _ <- blocking.effectBlocking(client.putObject(bucket, key, result.asJson.spaces2SortKeys))
            } yield ()
          }
        }
    }

  }

}
