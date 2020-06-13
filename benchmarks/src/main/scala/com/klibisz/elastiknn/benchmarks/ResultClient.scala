package com.klibisz.elastiknn.benchmarks

import com.amazonaws.services.s3.AmazonS3
import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery}
import io.circe.parser.decode
import io.circe.syntax._
import zio._
import codecs._
import com.amazonaws.services.s3.model.{ListObjectsV2Request, ListObjectsV2Result}
import zio.blocking._
import zio.stream._

import scala.annotation.tailrec
import scala.collection.JavaConverters._

object ResultClient {

  trait Service {
    def find(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): IO[Throwable, Option[BenchmarkResult]]
    def save(result: BenchmarkResult): IO[Throwable, Unit]
    def all(): Stream[Throwable, BenchmarkResult]
  }

  def s3(bucket: String, keyPrefix: String): ZLayer[Has[AmazonS3] with Blocking, Nothing, ResultClient] = {

    def validChars(s: String): String = s.map { c =>
      if (c.isLetter && c <= 'z' || c.isDigit || c == '.') c
      else '-'
    }

    def genKey(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): String = {
      val suffix = validChars(s"res-${dataset.toString}-${mapping.toString}-${query.toString}-$k.json")
      if (keyPrefix.nonEmpty) s"$keyPrefix/$suffix".replace("//", "/")
      else suffix
    }

    ZLayer.fromServices[AmazonS3, Blocking.Service, Service] {
      case (client, blocking) =>
        new Service {
          override def find(dataset: Dataset,
                            mapping: Mapping,
                            query: NearestNeighborsQuery,
                            k: Int): IO[Throwable, Option[BenchmarkResult]] = {
            val key = genKey(dataset, mapping, query, k)
            for {
              ex <- blocking.effectBlocking(client.doesObjectExist(bucket, key))
              res: Option[BenchmarkResult] <- if (ex) {
                for {
                  body <- blocking.effectBlocking(client.getObjectAsString(bucket, key))
                  dec <- ZIO.fromEither(decode[BenchmarkResult](body))
                } yield Some(dec)
              } else ZIO.effectTotal(None)
            } yield res
          }

          override def save(result: BenchmarkResult): IO[Throwable, Unit] = {
            val key = genKey(result.dataset, result.mapping, result.query, result.k)
            for {
              bucketExists <- blocking.effectBlocking(client.doesBucketExistV2(bucket))
              _ <- if (bucketExists) ZIO.succeed(()) else blocking.effectBlocking(client.createBucket(bucket))
              _ <- blocking.effectBlocking(client.putObject(bucket, key, result.asJson.spaces2SortKeys))
            } yield ()
          }

          override def all(): Stream[Throwable, BenchmarkResult] = {

            @tailrec
            def readAllKeys(req: ListObjectsV2Request, agg: Vector[String] = Vector.empty): Vector[String] = {
              val batchRes = client.listObjectsV2(req)
              val batchKeys = batchRes.getObjectSummaries.asScala.toVector.map(_.getKey)
              if (batchRes.isTruncated) agg ++ batchKeys
              else readAllKeys(req.withContinuationToken(batchRes.getContinuationToken), agg ++ batchKeys)
            }

            val req = new ListObjectsV2Request()
              .withBucketName(bucket)
              .withPrefix(keyPrefix)
              .withMaxKeys(1000)

            Stream
              .fromIterableM(blocking.effectBlocking(readAllKeys(req)))
              .mapM(key => blocking.effectBlocking(client.getObjectAsString(bucket, key)))
              .mapM(body => ZIO.fromEither(decode[BenchmarkResult](body)))
          }
        }
    }

  }

}
