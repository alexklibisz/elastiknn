package com.klibisz.elastiknn.server

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.server.LuceneIndex._
import org.apache.lucene.document.{Document, StoredField}
import org.apache.lucene.index.IndexNotFoundException

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

final class FakeElasticsearch {

  private implicit def flipTry[T](trys: Iterable[Try[T]]): Try[Seq[T]] = {
    @tailrec
    def rec(trys: Vector[Try[T]], acc: Vector[T]): Try[Vector[T]] = trys.headOption match {
      case None             => Success(acc)
      case Some(Success(t)) => rec(trys.tail, acc :+ t)
      case Some(Failure(t)) => Failure(t)
    }
    rec(trys.toVector, Vector.empty)
  }

  private implicit def flipUnitTry[T](trys: Iterable[Try[T]]): Try[Unit] = flipTry(trys.map(_.map(_ => ()))).map(_.head)

  private val state: mutable.Map[String, LuceneIndex] = mutable.Map.empty

  private def noSuchIndex(index: String): IndexNotFoundException = new IndexNotFoundException(s"No such index [$index]")

  def createIndex(index: String): Try[Unit] =
    if (state.contains(index)) Failure(new IllegalStateException(s"Index [$index] already exists"))
    else Success(state.put(index, LuceneIndex.Empty(index)))

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

  def bulkIndex(requests: Vector[IndexRequest]): Try[Unit] =
    requests
      .groupBy(_.index)
      .map {
        case (index, requests) => bulkIndex(index, requests)
      }

  def bulkIndex(index: String, requests: Vector[IndexRequest]): Try[Unit] =
    state.get(index) match {
      case Some(_: Empty)  => Failure(new IllegalStateException(s"index [$index] doesn't have a mapping"))
      case Some(_: Closed) => Failure(new IllegalStateException(s"index [$index] is closed for writing"))
      case None            => Failure(noSuchIndex(index))
      case Some(Open(_, mapping, indexWriter)) =>
        val docTrys = requests.map {
          case IndexRequest(_, id, vec) =>
            val fieldsTry = vec match {
              case dfv: Vec.DenseFloat => VectorMapper.denseFloatVector.checkAndCreateFields(mapping, "vec", dfv)
              case sbv: Vec.SparseBool => VectorMapper.sparseBoolVector.checkAndCreateFields(mapping, "vec", sbv)
              case _                   => Failure(new IllegalArgumentException(s"vector [$vec] cannot be indexed"))
            }
            fieldsTry.map { fields =>
              val doc = new Document
              doc.add(new StoredField("id", id))
              fields.foreach(doc.add)
              doc
            }
        }
        flipTry(docTrys).map(docs => indexWriter.addDocuments(docs.asJava)).map(_ => ())
    }

}
