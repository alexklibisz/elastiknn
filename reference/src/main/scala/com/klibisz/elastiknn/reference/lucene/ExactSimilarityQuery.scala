package com.klibisz.elastiknn.reference.lucene

import java.io.File
import java.util
import java.util.Objects

import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
import com.klibisz.elastiknn.models.{ExactSimilarityModelOld, ExactSimilarityScore}
import com.klibisz.elastiknn.utils.ElastiKnnVectorUtils
import com.klibisz.elastiknn.{ElastiKnnVector, Similarity, SparseBoolVector}
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.codecs.simpletext.SimpleTextCodec
import org.apache.lucene.document.{BinaryDocValuesField, Document, Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.util.BytesRef

object ExactSimilarityQuery extends ElastiKnnVectorUtils {

  class ExactSimilarityQuery(val field: String, val sim: Similarity, val queryVector: ElastiKnnVector) extends Query {

    private val existsQuery = new DocValuesFieldExistsQuery(field)

    class ExactSimWeight(searcher: IndexSearcher) extends Weight(this) {

      private val existsWeight = existsQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)

      override def extractTerms(terms: util.Set[Term]): Unit = ()

      override def explain(context: LeafReaderContext, doc: Int): Explanation = ???

      override def scorer(context: LeafReaderContext): Scorer = {
        val existsScorer = existsWeight.scorer(context)
        val existsIterator = if (existsScorer != null) existsScorer.iterator() else DocIdSetIterator.empty()
        new ExactSimScorer(this, context, existsIterator)
      }

      override def isCacheable(ctx: LeafReaderContext): Boolean = false
    }

    class ExactSimScorer(weight: Weight, context: LeafReaderContext, iterator: DocIdSetIterator) extends Scorer(weight) {

      private val reader = context.reader()
      private val binaryDocValues = reader.getBinaryDocValues(field)

      override def iterator(): DocIdSetIterator = iterator

      override def getMaxScore(upTo: Int): Float = Float.MaxValue

      override def score(): Float =
        if (binaryDocValues.advanceExact(iterator.docID())) {
          val bv = binaryDocValues.binaryValue()
          val ekv = ElastiKnnVector.parseFrom(bv.bytes.take(bv.length))
          val ExactSimilarityScore(score, _) = ExactSimilarityModelOld.apply(sim, queryVector, ekv).get
          score.toFloat
        } else 0f

      override def docID(): Int = iterator.docID()
    }

    override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight =
      new ExactSimWeight(searcher)

    override def toString(field: String): String = s"$field:${sim.name}"

    override def equals(other: Any): Boolean = other match {
      case esq: ExactSimilarityQuery => esq.field == this.field && esq.sim == this.sim
      case _                         => false
    }

    override def hashCode(): Int = Objects.hash(field, sim)

  }

  def main(args: Array[String]): Unit = {

    val tmpDir = new File(s"/tmp/lucene-backward-${System.currentTimeMillis()}")
    val ixDir = new MMapDirectory(tmpDir.toPath)

    val ixWriterCfg = new IndexWriterConfig(new WhitespaceAnalyzer).setCodec(new SimpleTextCodec)
    val ixWriter = new IndexWriter(ixDir, ixWriterCfg)

    val idFieldType = new FieldType()
    idFieldType.setStored(true)
    idFieldType.setIndexOptions(IndexOptions.DOCS)

    val field = "vec_raw"
    val queryVector = ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(SparseBoolVector(Array(1, 2, 3), 10)))
    val sbvOpts = Seq(
      Some(SparseBoolVector(Array(1, 2, 3), 10)),
      None,
      Some(SparseBoolVector(Array(1, 3, 5), 10)),
      Some(SparseBoolVector(Array(3, 6, 7, 8), 10))
    )

    sbvOpts.zipWithIndex.foreach {
      case (sbvOpt, i) =>
        val doc = new Document
        doc.add(new Field("id", i.toString, idFieldType))
        sbvOpt.foreach { sbv =>
          val ekv = ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv))
          doc.add(new BinaryDocValuesField(field, new BytesRef(ekv.toByteArray)))
        }
        ixWriter.addDocument(doc)
    }

    ixWriter.forceMerge(1)
    ixWriter.commit()
    ixWriter.close()

    val ixReader = DirectoryReader.open(ixDir)
    val ixSearcher = new IndexSearcher(ixReader)

    val topDocs = ixSearcher.search(new ExactSimilarityQuery(field, SIMILARITY_JACCARD, queryVector), 10)

    println(s"Found ${topDocs.scoreDocs.length} docs")
    topDocs.scoreDocs.foreach(d => println(s"${d.doc}, ${d.score}, ${ixSearcher.doc(d.doc)}"))

  }

}
