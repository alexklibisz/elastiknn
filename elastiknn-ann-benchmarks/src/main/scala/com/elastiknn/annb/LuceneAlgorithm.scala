package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.{ExactModel, L2LshModel}
import com.klibisz.elastiknn.storage.UnsafeSerialization
import io.circe.{Decoder, Json}
import org.apache.lucene.document.{Field, FieldType, StoredField}
import org.apache.lucene.index.{IndexOptions, IndexReader, IndexableField, LeafReaderContext}
import org.apache.lucene.search.{MatchHashesAndScoreQuery, Query}

import scala.util.{Failure, Success, Try}

trait LuceneAlgorithm[V <: Vec.KnownDims] {

  def toDocument(id: Long, vec: V): java.lang.Iterable[IndexableField]

  def queryBuilder(queryArgs: Json, indexReader: IndexReader): Try[V => Query]

}

object LuceneAlgorithm {

  private val idFieldName = "id"
  private val vecFieldName = "v"

  sealed trait ElastiknnLuceneTypes {
    protected val idFieldType = new FieldType()
    idFieldType.setStored(true)

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
      fields.add(new Field(idFieldName, s"v$id", idFieldType))
      fields.add(new StoredField(vecFieldName, UnsafeSerialization.writeFloats(vec.values)))
      hashes.foreach(hf => fields.add(new Field(vecFieldName, hf.hash, vecFieldType)))
      fields
    }

    override def queryBuilder(queryArgs: Json, indexReader: IndexReader): Try[Vec.DenseFloat => Query] = {
      Decoder[(Int, Int)]
        .decodeJson(queryArgs)
        .fold(Failure(_), Success(_))
        .map {
          case (candidates, probes) =>
            (vec: Vec.DenseFloat) =>
              new MatchHashesAndScoreQuery(
                vecFieldName,
                m.hash(vec.values, probes),
                candidates,
                indexReader,
                (lrc: LeafReaderContext) => {
                  val binaryDocValues = lrc.reader().getBinaryDocValues(vecFieldName)
                  (docID: Int, _: Int) =>
                    val prevDocID = binaryDocValues.docID()
                    val storedVec: Array[Float] = if (prevDocID == docID || binaryDocValues.advanceExact(docID)) {
                      val bytesRef = binaryDocValues.binaryValue()
                      UnsafeSerialization.readFloats(bytesRef.bytes, bytesRef.offset, bytesRef.length)
                    } else
                      throw new RuntimeException(s"Could not advance binary doc values reader from doc ID [$prevDocID] to doc ID [$docID].")
                    exact.similarity(vec.values, storedVec)
                }
              )
        }
    }
  }
}
