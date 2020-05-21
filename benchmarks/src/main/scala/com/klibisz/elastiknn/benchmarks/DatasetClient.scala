package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.amazonaws.services.s3.AmazonS3
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Vec}
import io.circe
import zio._
import zio.stream._

import scala.io.Source

object DatasetClient {

  trait Service {
    def stream[V <: Vec: ElasticsearchCodec](dataset: Dataset, limit: Option[Int] = None): Stream[Throwable, V]
  }

  def s3(bucket: String, keyPrefix: String): ZLayer[Has[AmazonS3], Throwable, DatasetClient] = ???

  def default: Layer[Throwable, DatasetClient] = local(new File(s"${System.getProperty("user.home")}/.elastiknn-data"))

  /** Implementation of [[DatasetClient.Service]] that reads from a local directory. */
  def local(datasetsDirectory: File): Layer[Throwable, DatasetClient] = ZLayer.fromFunction { _ =>
    new Service {
      override def stream[V <: Vec: ElasticsearchCodec](dataset: Dataset, limit: Option[Int] = None): Stream[Throwable, V] = {
        def parseDecode(s: String): Either[circe.Error, V] =
          ElasticsearchCodec.parse(s).flatMap(j => ElasticsearchCodec.decode[V](j.hcursor))
        val path = s"${datasetsDirectory.getAbsolutePath}/${dataset.name}/vecs.json"
        val iterManaged = Managed.makeEffect(Source.fromFile(path))(_.close())
        val lines = Stream.fromIteratorManaged(iterManaged.map(src => limit.map(n => src.getLines.take(n)).getOrElse(src.getLines)))
        val rawJson = lines.map(_.dropWhile(_ != '{')) // Drop until the Json starts.
        rawJson.mapM(s => ZIO.fromEither(parseDecode(s)))
      }
    }
  }

}
