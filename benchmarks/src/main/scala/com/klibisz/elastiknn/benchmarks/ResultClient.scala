package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.net.URI
import java.nio.file.{Files, Path}

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping, NearestNeighborsQuery}
import io.circe.syntax._
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import zio._

object ResultClient {

  trait Service {
    def all: IO[Throwable, Vector[Result]]
    def find(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): IO[Throwable, Option[Result]]
    def save(result: Result): IO[Throwable, Unit]
  }

  def s3(resultsURI: URI): Layer[Throwable, ResultClient] = {

    ???
  }

  def local(resultsFile: File): Layer[Throwable, ResultClient] = ZLayer.succeed {
    new Service {
      private implicit val datasetCodec: Codec[Dataset] = deriveEnumerationCodec[Dataset]
      private implicit val mappingCodec: Codec[Mapping] = ElasticsearchCodec.mapping
      private implicit val queryCodec: Codec[NearestNeighborsQuery] = ElasticsearchCodec.nearestNeighborsQuery
      private implicit val resultCodec: Codec[Result] = deriveCodec[Result]
      private val lock: UIO[Semaphore] = Semaphore.make(1)

      override def all: IO[Throwable, Vector[Result]] = {
        val read = for {
          ex <- ZIO.effect(Files.exists(resultsFile.toPath))
          s <- if (ex) ZIO.effect(Files.readString(resultsFile.toPath)) else ZIO.effectTotal("")
          rr <- if (s.nonEmpty) ZIO.fromEither(decode[Vector[Result]](s)) else ZIO.effectTotal(Vector.empty)
        } yield rr
        lock.flatMap(_.withPermit(read))
      }

      override def find(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): IO[Throwable, Option[Result]] =
        for {
          rr <- all
          find = rr.find(r => r.dataset == dataset && r.mapping == mapping && r.query == query && r.k == k)
        } yield find

      override def save(result: Result): IO[Throwable, Unit] = {
        val write = for {
          rr <- all
          s = (rr :+ result).asJson.spaces2
          _ <- ZIO.effect(Files.writeString(resultsFile.toPath, s))
        } yield ()
        lock.flatMap(_.withPermit(write))
      }
    }
  }

}
