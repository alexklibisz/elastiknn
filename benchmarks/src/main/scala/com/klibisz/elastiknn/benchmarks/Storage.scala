package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.nio.file.{Files, Path}

import com.klibisz.elastiknn.api._
import io.circe.parser._
import io.circe.syntax._
import zio._
import zio.stream._
import codecs._

import scala.language.implicitConversions

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

  /** Return a result for the given parameters if it exists. */
  def findResult(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): Task[Result]

  /** Return all saved results. */
  def allResults: Task[Set[Result]]

  /** Save the given result. */
  def saveResult(result: Result): Task[Unit]

}

/** Quite dumb Storage implementation that stores all results on disk without any internal state. */
final class FileStorage(datasetsDirectory: File, databaseFile: File) extends Storage[File] {

  private implicit def toPath(f: File): Path = f.toPath
  private val fileSem: UIO[Semaphore] = Semaphore.make(1)

  override protected def streamVectorReferences(dataset: Dataset): Stream[Throwable, File] = {
    Stream.fromIterableM(ZIO {
      val dir = new File(s"${datasetsDirectory.getAbsolutePath}/${dataset.name}/vecs")
      dir.listFiles
    })
  }

  override protected def parseVector[V <: Vec: ElasticsearchCodec](ref: File): Task[V] = {
    for {
      s <- ZIO(Files.readString(ref))
      v <- ZIO.fromEither(ElasticsearchCodec.parse(s).flatMap(implicitly[ElasticsearchCodec[V]].decodeJson))
    } yield v
  }

  /** Return a result for the given parameters if it exists. */
  override def findResult(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int): Task[Result] =
    for {
      results <- allResults
      found = results.find(r => r.dataset == dataset && r.mapping == mapping && r.query == query && r.k == k) match {
        case Some(res) => scala.util.Success(res)
        case None      => scala.util.Failure(new NoSuchElementException(s"No matching result found"))
      }
      res <- ZIO.fromTry(found)
    } yield res

  /** Save the given result. */
  override def saveResult(result: Result): Task[Unit] = {
    val writeTask = for {
      results <- allResults
      s = (results + result).asJson.spaces2
      _ <- ZIO.effect(Files.writeString(databaseFile, s))
    } yield ()
    fileSem.flatMap(_.withPermit(writeTask))
  }

  /** Return all saved results. */
  override def allResults: Task[Set[Result]] = {
    val readTask = for {
      s <- ZIO.effect(Files.readString(databaseFile))
      results <- ZIO.fromEither(decode[Set[Result]](s))
    } yield results
    fileSem.flatMap(_.withPermit(readTask))
  }

  override def toString: String = s"FileStorage reading from $datasetsDirectory, writing to $databaseFile"

}

object FileStorage {
  val eknnDirectory: File = new File(s"${System.getProperty("user.home")}/.elastiknn-data")
}
