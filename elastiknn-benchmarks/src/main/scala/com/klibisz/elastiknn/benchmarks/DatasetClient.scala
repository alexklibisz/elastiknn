package com.klibisz.elastiknn.benchmarks

import java.util.zip.GZIPInputStream

import com.amazonaws.services.s3.AmazonS3
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Vec}
import com.klibisz.elastiknn.benchmarks.Dataset._
import io.circe
import zio._
import zio.stream._

import scala.io.Source
import scala.util.Random
import scala.util.hashing.MurmurHash3

trait DatasetClient {
  def streamTrain(dataset: Dataset, limit: Option[Int] = None): Stream[Throwable, Vec]
  def streamTest(dataset: Dataset, limit: Option[Int] = None): Stream[Throwable, Vec]
}

object DatasetClient {

  def s3(bucket: String, keyPrefix: String): ZLayer[Has[AmazonS3], Throwable, Has[DatasetClient]] =
    ZLayer.fromService[AmazonS3, DatasetClient] { client =>
      new DatasetClient {

        private def stream(dataset: Dataset, name: String, limit: Option[Int]): Stream[Throwable, Vec] = dataset match {
          case S3Pointer(_, _, _) => ???
          case _ =>
            val key = s"$keyPrefix/${dataset.name}/$name.json.gz".toLowerCase
            val obj = client.getObject(bucket, key)
            val iterManaged = Managed.makeEffect(Source.fromInputStream(new GZIPInputStream(obj.getObjectContent)))(_.close())
            val lines = Stream.fromIteratorManaged(iterManaged.map(src => limit.map(n => src.getLines.take(n)).getOrElse(src.getLines())))
            val rawJson = lines.map(_.dropWhile(_ != '{'))
            rawJson.mapM(s => ZIO.fromEither(parseDecode(s)))
        }

        private def parseDecode(s: String): Either[circe.Error, Vec] =
          ElasticsearchCodec.parse(s).flatMap(j => ElasticsearchCodec.decode[Vec](j.hcursor))

        override def streamTrain(dataset: Dataset, limit: Option[Int]): Stream[Throwable, Vec] =
          stream(dataset, "train", limit)

        override def streamTest(dataset: Dataset, limit: Option[Int]): Stream[Throwable, Vec] =
          stream(dataset, "test", limit)
      }
    }

}
