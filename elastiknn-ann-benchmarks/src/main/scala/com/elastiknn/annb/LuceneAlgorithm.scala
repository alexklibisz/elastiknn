package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.{ExactModel, L2LshModel}
import com.klibisz.elastiknn.storage.UnsafeSerialization
import io.circe.{Decoder, Json}
import org.apache.lucene.document.{BinaryDocValuesField, Field, FieldType, StoredField}
import org.apache.lucene.index.{IndexOptions, IndexReader, IndexableField, LeafReaderContext}
import org.apache.lucene.search.{IndexSearcher, MatchHashesAndScoreQuery, Query}
import org.apache.lucene.util.BytesRef

import java.util
import java.util.concurrent.Executor
import scala.concurrent.duration._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

trait LuceneAlgorithm[V <: Vec.KnownDims] {

  def toDocument(id: Long, vec: V): java.lang.Iterable[IndexableField]

  def searchFunction(queryArgs: Json, indexReader: IndexReader, searchExecutor: Executor): Try[(V, Int) => LuceneResult]

}

object LuceneAlgorithm {

  private val idFieldName = "id"
  private val vecFieldName = "v"

  sealed trait ElastiknnLuceneTypes {
    protected val idFieldType = new FieldType()
    idFieldType.setStored(true)

    protected val storedFieldsIdOnly: util.Set[String] = new util.HashSet[String] {
      add(idFieldName)
    }

    protected val vecFieldType = new FieldType()
    vecFieldType.setStored(false)
    vecFieldType.setOmitNorms(true)
    vecFieldType.setIndexOptions(IndexOptions.DOCS)
    vecFieldType.setTokenized(false)
    vecFieldType.setStoreTermVectors(false)
  }

  final class ElastiknnL2Lsh(m: L2LshModel) extends LuceneAlgorithm[Vec.DenseFloat] with ElastiknnLuceneTypes {

    private val exact = new ExactModel.L2

    override def toDocument(id: Long, vec: Vec.DenseFloat): java.lang.Iterable[IndexableField] = {
      val hashes = m.hash(vec.values)
      // TODO: compare perf of ArrayList vs. LinkedList.
      val fields = new java.util.ArrayList[IndexableField](hashes.length + 2)
      fields.add(new Field(idFieldName, id.toString, idFieldType))
      fields.add(new BinaryDocValuesField(vecFieldName, new BytesRef(UnsafeSerialization.writeFloats(vec.values))))
      hashes.foreach(hf => fields.add(new Field(vecFieldName, hf.hash, vecFieldType)))
      fields
    }

    override def searchFunction(
        queryArgs: Json,
        indexReader: IndexReader,
        searchExecutor: Executor
    ): Try[(Vec.DenseFloat, Int) => LuceneResult] = {
      val indexSearcher = new IndexSearcher(indexReader, searchExecutor)
      Decoder[(Int, Int)]
        .decodeJson(queryArgs)
        .fold(Failure(_), Success(_))
        .map {
          case (candidates, probes) =>
            (vec: Vec.DenseFloat, count: Int) =>
              val query = new MatchHashesAndScoreQuery(
                vecFieldName,
                m.hash(vec.values, probes),
                candidates,
                indexReader,
                (lrc: LeafReaderContext) => {
                  // TODO: Dedup the logic here and in StoredVecReader.
                  val binaryDocValues = lrc.reader().getBinaryDocValues(vecFieldName)
                  (docID: Int, _: Int) =>
                    val prevDocID = binaryDocValues.docID()
                    if (prevDocID == docID || binaryDocValues.advanceExact(docID)) {
                      val bytesRef = binaryDocValues.binaryValue()
                      val values = UnsafeSerialization.readFloats(bytesRef.bytes, bytesRef.offset, bytesRef.length)
                      exact.similarity(vec.values, values)
                    } else throw new RuntimeException(s"Could not advance to doc ID [$docID].")
                }
              )
              val indexes = new ArrayBuffer[Int](count)
              val distances = new ArrayBuffer[Float](count)
              val t0 = System.nanoTime()
              indexSearcher.search(query, count).scoreDocs.foreach { td =>
                val doc = indexReader.document(td.doc, storedFieldsIdOnly)
                indexes.append(doc.getField("id").stringValue().toInt)
                distances.append(1 / td.score - 1)
              }
              LuceneResult((System.nanoTime() - t0).nanos, indexes.toArray, distances.toArray)
        }
    }
  }
}
