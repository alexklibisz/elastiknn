package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api._
import io.circe.Json
import zio._
import zio.stream._

import scala.io.{BufferedSource, Source}

trait Storage {

  /** Access the given dataset as a [[BufferedSource]]. */
  def openDataset(dataset: Dataset): Managed[Throwable, BufferedSource]

  /** Create a resource safe stream over vectors in a dataset. */
  final def streamDataset[V <: Vec: ElasticsearchCodec](dataset: Dataset): Stream[Throwable, V] = {
    val iteratorManaged = openDataset(dataset).map(_.getLines())
    val stringStream: Stream[Throwable, String] = Stream.fromIteratorManaged(iteratorManaged)
    val jsonStream: Stream[Throwable, Json] = stringStream.mapM(s => ZIO.fromEither(ElasticsearchCodec.parse(s)))
    val vecStream: Stream[Throwable, V] = jsonStream.mapM(j => ZIO.fromEither(ElasticsearchCodec.decode[V](j.hcursor)))
    vecStream
  }

}

final class FileStorage(datasetsDirectory: File, databaseFile: File) extends Storage {

  /** Access the given dataset as a [[BufferedSource]]. */
  override def openDataset(dataset: Dataset): Managed[Throwable, BufferedSource] =
    Managed.makeEffect(Source.fromFile(s"${datasetsDirectory.getAbsolutePath}/${dataset.name}.json"))(_.close())

}
