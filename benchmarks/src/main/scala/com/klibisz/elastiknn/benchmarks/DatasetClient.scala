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

object DatasetClient {

  trait Service {
    def streamTrain[V <: Vec: ElasticsearchCodec](dataset: Dataset, limit: Option[Int] = None): Stream[Throwable, V]
    def streamTest[V <: Vec: ElasticsearchCodec](dataset: Dataset, limit: Option[Int] = None): Stream[Throwable, V]
  }

  /** Implementation of [[DatasetClient.Service]] that reads from an s3 bucket. */
  def s3(bucket: String, keyPrefix: String): ZLayer[Has[AmazonS3], Throwable, DatasetClient] = ZLayer.fromService[AmazonS3, Service] {
    client =>
      new Service {
        private def stream[V <: Vec: ElasticsearchCodec](dataset: Dataset, name: String, limit: Option[Int]): Stream[Throwable, V] =
          dataset match {
            case r: RandomSparseBool =>
              implicit val rng: Random = new Random(MurmurHash3.orderedHash(Seq(r.dims, name)))
              Stream
                .range(0, if (name == "train") r.count else r.count / 10)
                .map(_ => Vec.SparseBool.random(r.dims))
                .map(ElasticsearchCodec.encode(_).hcursor)
                .map(ElasticsearchCodec.decode[V](_))
                .mapM(ZIO.fromEither(_))
            case r: RandomDenseFloat =>
              implicit val rng: Random = new Random(MurmurHash3.orderedHash(Seq(r.dims, name)))
              Stream
                .range(0, if (name == "train") r.count else r.count / 10)
                .map(_ => Vec.DenseFloat.random(r.dims))
                .map(ElasticsearchCodec.encode(_).hcursor)
                .map(ElasticsearchCodec.decode[V](_))
                .mapM(ZIO.fromEither(_))
            case _ =>
              def parseDecode(s: String): Either[circe.Error, V] =
                ElasticsearchCodec.parse(s).flatMap(j => ElasticsearchCodec.decode[V](j.hcursor))
              val obj = client.getObject(bucket, s"$keyPrefix/${dataset.name}/${name}.json.gz")
              val iterManaged = Managed.makeEffect(Source.fromInputStream(new GZIPInputStream(obj.getObjectContent)))(_.close())
              val lines = Stream.fromIteratorManaged(iterManaged.map(src => limit.map(n => src.getLines.take(n)).getOrElse(src.getLines())))
              val rawJson = lines.map(_.dropWhile(_ != '{'))
              rawJson.mapM(s => ZIO.fromEither(parseDecode(s)))
          }

        override def streamTrain[V <: Vec: ElasticsearchCodec](dataset: Dataset, limit: Option[Int]): Stream[Throwable, V] =
          stream(dataset, "train", limit)

        override def streamTest[V <: Vec: ElasticsearchCodec](dataset: Dataset, limit: Option[Int]): Stream[Throwable, V] =
          stream(dataset, "test", limit)
      }
  }

}
