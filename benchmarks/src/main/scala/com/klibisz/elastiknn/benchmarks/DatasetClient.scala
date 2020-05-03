package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api.{ElasticsearchCodec, Vec}
import io.circe
import zio._
import zio.stream._

import scala.io.Source

object DatasetClient {

  trait Service {
    def streamVectors[V <: Vec: ElasticsearchCodec](dataset: Dataset): Stream[Throwable, V]
  }

  def default: Layer[Throwable, DatasetClient] = local(new File(s"${System.getProperty("user.home")}/.elastiknn-data"))

  /** Implementation of [[DatasetClient.Service]] that reads from a local directory. */
  def local(datasetsDirectory: File): Layer[Throwable, DatasetClient] = ZLayer.fromFunction { _ =>
    new Service {
      override def streamVectors[V <: Vec: ElasticsearchCodec](dataset: Dataset): Stream[Throwable, V] = {
        def parseDecode(s: String): Either[circe.Error, V] =
          ElasticsearchCodec.parse(s).flatMap(j => ElasticsearchCodec.decode[V](j.hcursor))
        val path = s"${datasetsDirectory.getAbsolutePath}/${dataset.name}/vecs_4096.json"
        val iterManaged = Managed.makeEffect(Source.fromFile(path))(_.close())
        val lines = Stream.fromIteratorManaged(iterManaged.map(_.getLines()))
        val rawJson = lines.map(_.dropWhile(_ != '{')) // Drop until the Json starts.
        rawJson.mapM(s => ZIO.fromEither(parseDecode(s)))
      }
    }
  }

}
