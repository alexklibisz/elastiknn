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
import org.apache.lucene.util.BytesRef

object LuceneJaccardIndexedQuery extends ElastiKnnVectorUtils {

  object JaccardIndexedQuery {

    def fieldTrueIndices(field: String): String = s"$field.true_indices"
    def fieldNumTrue(field: String): String = s"$field.num_true"

    private val trueIndicesFieldType: FieldType = {
      val ft = new FieldType()
      ft.setIndexOptions(IndexOptions.DOCS)
      ft.setTokenized(false)
      ft
    }

    private def intToBytes(i: Int): BytesRef = {
      val buf = java.nio.ByteBuffer.allocate(4)
      buf.putInt(i)
      new BytesRef(buf.array())
    }

    def document(field: String, sbv: SparseBoolVector): Document = {
      val d = new Document
      sbv.trueIndices.foreach { ti =>
        d.add(new Field(JaccardIndexedQuery.fieldTrueIndices(field), intToBytes(ti), trueIndicesFieldType))
      }
      d.add(new StoredField(JaccardIndexedQuery.fieldNumTrue(field), sbv.trueIndices.length))
      d
    }

  }

  class JaccardIndexedQuery(val field: String, val queryVector: SparseBoolVector) extends Query {

    import JaccardIndexedQuery._

    private val booleanIntersectionQuery: BooleanQuery = {
      val bqb = new BooleanQuery.Builder
      queryVector.trueIndices.foreach { ti =>
        bqb.add(new BooleanClause(new TermQuery(new Term(fieldTrueIndices(field), intToBytes(ti))), BooleanClause.Occur.SHOULD))
      }
      bqb.build()
    }

    class ExactSimWeight(searcher: IndexSearcher) extends Weight(this) {

      // This makes the boolean query count the actual intersections, instead of doing something fancier.
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

    val ixWriterCfg = new IndexWriterConfig().setCodec(new SimpleTextCodec)
    val ixWriter = new IndexWriter(ixDir, ixWriterCfg)

    val idFieldType = new FieldType()
    idFieldType.setStored(true)
    idFieldType.setIndexOptions(IndexOptions.DOCS)

    val field = "vec_raw"
    val queryVector = SparseBoolVector(Array(1, 2, 3), 10)
    val sbvs = Seq(
      SparseBoolVector(Array(1, 2, 3), 10),
      SparseBoolVector(Array(1, 3, 5), 10),
      SparseBoolVector(Array(3, 6, 7, 8), 10),
      SparseBoolVector(Array(1, 4, 5, 6, 7, 8, 9), 10)
    )

    sbvs.zipWithIndex.foreach {
      case (sbv, i) =>
        val doc = JaccardIndexedQuery.document(field, sbv)
        doc.add(new Field("id", i.toString, idFieldType))
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
