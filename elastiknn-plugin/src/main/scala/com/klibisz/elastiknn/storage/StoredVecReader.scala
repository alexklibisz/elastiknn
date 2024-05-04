package com.klibisz.elastiknn.storage

import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
import com.klibisz.elastiknn.storage.StoredVec.Decoder

import org.apache.lucene.index.LeafReaderContext

final class StoredVecReader[S <: StoredVec: Decoder](lrc: LeafReaderContext, field: String) {
  // Important for performance that each of these is instantiated here and not in the apply method.
  private val vecDocVals = lrc.reader.getBinaryDocValues(field)
  private val decoder = summon[StoredVec.Decoder[S]]

  def apply(docID: Int): S = {
    val prevDocID = vecDocVals.docID()
    if (prevDocID == docID || vecDocVals.advanceExact(docID)) {
      val bytesRef = vecDocVals.binaryValue()
      decoder(bytesRef.bytes, bytesRef.offset, bytesRef.length)
    } else {
      throw new ElastiknnRuntimeException(
        Seq(
          s"Could not advance binary doc values reader from doc ID [$prevDocID] to doc ID [$docID].",
          s"It is possible that the document [$docID] does not have a vector.",
          s"""Consider trying with an `exists` query: "exists": { "field": "${field}" }"""
        ).mkString(" ")
      )
    }
  }
}
