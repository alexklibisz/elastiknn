package com.klibisz.elastiknn.server

import com.klibisz.elastiknn.api.Mapping
import com.klibisz.elastiknn.server.FakeIndex._
import org.apache.lucene.index.IndexNotFoundException

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

final class FakeElasticsearch {

  private val state: mutable.Map[String, FakeIndex] = mutable.Map.empty

  private def noSuchIndex(index: String): IndexNotFoundException = new IndexNotFoundException(s"No such index [$index]")

  def createIndex(index: String): Try[Unit] =
    if (state.contains(index)) Failure(new IllegalStateException(s"Index [$index] already exists"))
    else Success(state.put(index, FakeIndex.Empty(index)))

  def putMapping(index: String, mapping: Mapping): Try[Unit] = {
    state.get(index) match {
      case Some(Empty(name))               => Success(state.put(index, Open(name, mapping)))
      case Some(_: Open) | Some(_: Closed) => Failure(new IllegalStateException(s"Index [$index] already has a mapping"))
      case None                            => Failure(noSuchIndex(index))
    }
  }

  def deleteIndex(index: String): Try[Unit] =
    if (state.contains(index)) Success(state.remove(index))
    else Failure(noSuchIndex(index))

  @tailrec
  def deleteIndex(indexes: Seq[String]): Try[Unit] =
    if (indexes.isEmpty) Success(())
    else
      deleteIndex(indexes.head) match {
        case Success(_) => deleteIndex(indexes.tail)
        case Failure(t) => Failure(t)
      }

  def deleteAll(): Try[Unit] = Success(state.clear())

  def numIndices(): Int = state.size

}
