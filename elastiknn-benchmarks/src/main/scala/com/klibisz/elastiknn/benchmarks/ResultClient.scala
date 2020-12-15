package com.klibisz.elastiknn.benchmarks

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.klibisz.elastiknn.benchmarks.codecs._
import io.circe.parser.decode
import io.circe.syntax._
import zio._
import zio.blocking._
import zio.stream._

import scala.annotation.tailrec
import scala.collection.JavaConverters._

trait ResultClient {
  def find(experiment: Experiment, query: Query): IO[Throwable, Option[BenchmarkResult]]
  def save(experiment: Experiment, query: Query, result: BenchmarkResult): IO[Throwable, Unit]
  def all(): Stream[Throwable, BenchmarkResult]
}

object ResultClient {

  def s3(bucket: String, keyPrefix: String): ZLayer[Has[AmazonS3] with Blocking, Nothing, Has[ResultClient]] = {

    def keyGen(exp: Experiment, query: Query): String =
      s"${keyPrefix}/${exp.copy(queries = Seq(query)).uuid}.json"

    ZLayer.fromServices[AmazonS3, Blocking.Service, ResultClient] {
      case (client, blocking) =>
        new ResultClient {
          override def find(experiment: Experiment, query: Query): IO[Throwable, Option[BenchmarkResult]] = {
            // This is a bit subtle. Use the given experiment with just this specific query to generate the key.
            val key = keyGen(experiment, query)
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

          override def save(experiment: Experiment, query: Query, result: BenchmarkResult): IO[Throwable, Unit] = {
            val key = keyGen(experiment, query)
            for {
              bucketExists <- blocking.effectBlocking(client.doesBucketExistV2(bucket))
              _ <- if (bucketExists) ZIO.succeed(()) else blocking.effectBlocking(client.createBucket(bucket))
              _ <- blocking.effectBlocking(client.putObject(bucket, key, result.asJson.noSpacesSortKeys))
            } yield ()
          }

          override def all(): Stream[Throwable, BenchmarkResult] = {

            @tailrec
            def readAllKeys(req: ListObjectsV2Request, agg: Vector[String] = Vector.empty): Vector[String] = {
              val res = client.listObjectsV2(req)
              val keys = res.getObjectSummaries.asScala.toVector.map(_.getKey).filter(_.endsWith(".json"))
              if (res.isTruncated) readAllKeys(req.withContinuationToken(res.getNextContinuationToken), agg ++ keys)
              else agg ++ keys
            }

            val req = new ListObjectsV2Request().withBucketName(bucket).withPrefix(keyPrefix)

            Stream
              .fromIterableM(blocking.effectBlocking(readAllKeys(req)))
              .mapM(key => blocking.effectBlocking(client.getObjectAsString(bucket, key)))
              .mapM(body => ZIO.fromEither(decode[BenchmarkResult](body)))
          }
        }
    }

  }

}
