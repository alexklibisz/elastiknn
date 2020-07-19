package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models.SparseIndexedSimilarityFunction
import com.klibisz.elastiknn.storage.UnsafeSerialization
import org.apache.lucene.document.{Field, NumericDocValuesField}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef
import org.elasticsearch.index.mapper.MappedFieldType

object SparseIndexedQuery {

  def apply(field: String, queryVec: Vec.SparseBool, simFunc: SparseIndexedSimilarityFunction, indexReader: IndexReader): Query = {

    val terms = queryVec.trueIndices.map(i => new BytesRef(UnsafeSerialization.writeInt(i)))

    val scoreFunction: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
      (lrc: LeafReaderContext) => {
        val numericDocValues = lrc.reader.getNumericDocValues(numTrueDocValueField(field))
        (docId: Int, matchingTerms: Int) =>
          if (numericDocValues.advanceExact(docId)) {
            val numTrue = numericDocValues.longValue.toInt
            simFunc(queryVec, matchingTerms, numTrue)
          } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
      }

    new MatchHashesAndScoreQuery(
      field,
      terms,
      indexReader.getDocCount(field),
      indexReader,
      scoreFunction
    )
  }

  def numTrueDocValueField(field: String): String = s"$field.num_true"

  def index(field: String, fieldType: MappedFieldType, vec: Vec.SparseBool): Seq[IndexableField] = {
    vec.trueIndices.map { ti =>
      new Field(field, UnsafeSerialization.writeInt(ti), fieldType)
    } ++ ExactQuery.index(field, vec) :+ new NumericDocValuesField(numTrueDocValueField(field), vec.trueIndices.length)
  }

}
