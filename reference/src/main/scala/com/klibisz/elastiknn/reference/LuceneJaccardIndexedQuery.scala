package com.klibisz.elastiknn.reference

import java.io.File
import java.util
import java.util.Objects

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.utils.ElastiKnnVectorUtils
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.codecs.simpletext.SimpleTextCodec
import org.apache.lucene.document.{Document, Field, FieldType, StoredField}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.store.MMapDirectory

object LuceneJaccardIndexedQuery extends ElastiKnnVectorUtils {

  object JaccardIndexedQuery {
    def fieldTrueIndices(field: String): String = s"$field.true_indices"
    def fieldNumTrue(field: String): String = s"$field.num_true"
  }

  class JaccardIndexedQuery(val field: String, val queryVector: SparseBoolVector) extends Query {

    import JaccardIndexedQuery._

    private val booleanIntersectionQuery: BooleanQuery = {
      val bqb = new BooleanQuery.Builder
      queryVector.trueIndices.foreach { ti =>
        bqb.add(new BooleanClause(new TermQuery(new Term(fieldTrueIndices(field), ti.toString)), BooleanClause.Occur.SHOULD))
      }
      bqb.build()
    }

    class ExactSimWeight(searcher: IndexSearcher) extends Weight(this) {

      searcher.setSimilarity(new BooleanSimilarity)

      private val booleanWeight = booleanIntersectionQuery.createWeight(searcher, ScoreMode.COMPLETE, 1f)

      override def extractTerms(terms: util.Set[Term]): Unit = ()

      override def explain(context: LeafReaderContext, doc: Int): Explanation = ???

      override def scorer(context: LeafReaderContext): Scorer =
        new ExactSimScorer(this, searcher, context, booleanWeight.scorer(context))

      override def isCacheable(ctx: LeafReaderContext): Boolean = false
    }

    class ExactSimScorer(weight: Weight, searcher: IndexSearcher, context: LeafReaderContext, booleanScorer: Scorer)
        extends Scorer(weight) {

      override val iterator: DocIdSetIterator = if (booleanScorer != null) booleanScorer.iterator() else DocIdSetIterator.empty()

      override def getMaxScore(upTo: Int): Float = Float.MaxValue

      override def score(): Float = {
        val intersection = booleanScorer.score()
        val docId = iterator.docID()
        val doc = searcher.doc(docId)
        val numTrue = doc.getField(fieldNumTrue(field)).numericValue.intValue()
        intersection / (queryVector.trueIndices.length + numTrue - intersection) * 1f
      }

      override def docID(): Int = iterator.docID()
    }

    override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight =
      new ExactSimWeight(searcher)

    override def toString(field: String): String = s"$field:$queryVector"

    override def equals(other: Any): Boolean = other match {
      case esq: JaccardIndexedQuery => esq.field == this.field && esq.queryVector == this.queryVector
      case _                        => false
    }

    override def hashCode(): Int = Objects.hash(field, queryVector)

  }

  def main(args: Array[String]): Unit = {

    val tmpDir = new File(s"/tmp/lucene-backward-${System.currentTimeMillis()}")
    val ixDir = new MMapDirectory(tmpDir.toPath)

    val ixWriterCfg = new IndexWriterConfig(new WhitespaceAnalyzer).setCodec(new SimpleTextCodec)
    val ixWriter = new IndexWriter(ixDir, ixWriterCfg)

    val idFieldType = new FieldType()
    idFieldType.setStored(true)
    idFieldType.setIndexOptions(IndexOptions.DOCS)

    val trueIndicesFieldType = new FieldType()
    trueIndicesFieldType.setIndexOptions(IndexOptions.DOCS)

    val field = "vec_raw"
    val queryVector = SparseBoolVector(Array(1, 2, 3), 10)
    val sbvOpts = Seq(
      Some(SparseBoolVector(Array(1, 2, 3), 10)),
      None,
      Some(SparseBoolVector(Array(1, 3, 5), 10)),
      Some(SparseBoolVector(Array(3, 6, 7, 8), 10)),
      Some(SparseBoolVector(Array(1, 4, 5, 6, 7, 8, 9), 10))
    )

    sbvOpts.zipWithIndex.foreach {
      case (sbvOpt, i) =>
        val doc = new Document
        doc.add(new Field("id", i.toString, idFieldType))
        sbvOpt.foreach { sbv =>
          sbv.trueIndices.foreach { ti =>
            doc.add(new Field(JaccardIndexedQuery.fieldTrueIndices(field), ti.toString, trueIndicesFieldType))
          }
          doc.add(new StoredField(JaccardIndexedQuery.fieldNumTrue(field), sbv.trueIndices.length))
        }
        ixWriter.addDocument(doc)
    }

    ixWriter.forceMerge(1)
    ixWriter.commit()
    ixWriter.close()

    val ixReader = DirectoryReader.open(ixDir)
    val ixSearcher = new IndexSearcher(ixReader)

    val topDocs = ixSearcher.search(new JaccardIndexedQuery(field, queryVector), 10)

    println(s"Found ${topDocs.scoreDocs.length} docs")
    topDocs.scoreDocs.foreach(d => println(s"${d.doc}, ${d.score}, ${ixSearcher.doc(d.doc)}"))

  }

}
