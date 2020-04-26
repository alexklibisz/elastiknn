package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.nio.file.Files

import com.klibisz.elastiknn.api._
import io.circe.Json
import zio._
import zio.stream._

import scala.io.{BufferedSource, Source}
import scala.util.Try

/**
  *
  * @tparam R Represents a reference to a vector.
  */
trait Storage[R] {

  protected def streamVectorReferences(dataset: Dataset): Stream[Throwable, R]

  protected def parseVector[V <: Vec: ElasticsearchCodec](ref: R): Task[V]

  /** Create a resource safe stream over vectors in a dataset. */
  final def streamDataset[V <: Vec: ElasticsearchCodec](dataset: Dataset): Stream[Throwable, V] =
    streamVectorReferences(dataset).mapM(parseVector[V](_))

}

final class FileStorage(datasetsDirectory: File, databaseFile: File) extends Storage[File] {

  override protected def streamVectorReferences(dataset: Dataset): Stream[Throwable, File] = {
    Stream.fromIterableM(ZIO {
      val dir = new File(s"${datasetsDirectory.getAbsolutePath}/${dataset.name}/vecs")
      dir.listFiles
    })
  }

  override protected def parseVector[V <: Vec: ElasticsearchCodec](ref: File): Task[V] = {
    for {
      s <- ZIO(Files.readString(ref.toPath))
      v <- ZIO.fromEither(ElasticsearchCodec.parse(s).flatMap(implicitly[ElasticsearchCodec[V]].decodeJson))
    } yield v
  }

}
