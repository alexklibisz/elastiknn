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

    def genKey(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): String =
      s"$keyPrefix${MurmurHash3.orderedHash(Seq(dataset, mapping, query, k))}.json"

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
              _ <- blocking.effectBlocking(client.putObject(bucket, key, result.asJson.noSpaces))
            } yield ()
          }
        }
    }

  }

//  def local(resultsFile: File): Layer[Throwable, ResultClient] = ZLayer.succeed {
//    new Service {
//
//      private val lock: UIO[Semaphore] = Semaphore.make(1)
//
////      override def all: IO[Throwable, Vector[Result]] = {
////        val read = for {
////          ex <- ZIO.effect(Files.exists(resultsFile.toPath))
////          s <- if (ex) ZIO.effect(Files.readString(resultsFile.toPath)) else ZIO.effectTotal("")
////          rr <- if (s.nonEmpty) ZIO.fromEither(decode[Vector[Result]](s)) else ZIO.effectTotal(Vector.empty)
////        } yield rr
////        lock.flatMap(_.withPermit(read))
////      }
//
//      override def find(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): IO[Throwable, Option[Result]] =
//        for {
//          rr <- all
//          find = rr.find(r => r.dataset == dataset && r.mapping == mapping && r.query == query && r.k == k)
//        } yield find
//
//      override def save(result: Result): IO[Throwable, Unit] = {
//        val write = for {
//          rr <- all
//          s = (rr :+ result).asJson.spaces2
//          _ <- ZIO.effect(Files.writeString(resultsFile.toPath, s))
//        } yield ()
//        lock.flatMap(_.withPermit(write))
//      }
//    }
//  }

}
