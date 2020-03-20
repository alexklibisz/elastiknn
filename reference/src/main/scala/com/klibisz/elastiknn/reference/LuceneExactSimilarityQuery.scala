package com.klibisz.elastiknn.reference

import java.io.File
import java.util
import java.util.Objects

import com.klibisz.elastiknn.Similarity
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.codecs.simpletext.SimpleTextCodec
import org.apache.lucene.document.{BinaryDocValuesField, Document, Field, FieldType, StoredField}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.util.BytesRef

import scala.collection.JavaConverters._

object LuceneExactSimilarityQuery {

  class ExactSimilarityQuery(val field: String, val sim: Similarity) extends Query {

    private val existsQuery = new DocValuesFieldExistsQuery(field)

    class ExactSimilarityWeight(searcher: IndexSearcher) extends Weight(this) {

      private val existsWeight = existsQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)

      override def extractTerms(terms: util.Set[Term]): Unit = ()

      override def explain(context: LeafReaderContext, doc: Int): Explanation = ???

      override def scorer(context: LeafReaderContext): Scorer = {
        val existsScorer = existsWeight.scorer(context)
        val existsIterator = if (existsScorer != null) existsScorer.iterator() else DocIdSetIterator.empty()
        new ExactSimilirityScorer(this, context, existsIterator)
      }

      override def isCacheable(ctx: LeafReaderContext): Boolean = false
    }

    class ExactSimilirityScorer(weight: Weight, context: LeafReaderContext, iterator: DocIdSetIterator) extends Scorer(weight) {
      override def iterator(): DocIdSetIterator = iterator

      override def getMaxScore(upTo: Int): Float = Float.MaxValue

      override def score(): Float = 42f

      override def docID(): Int = iterator.docID()
    }

    override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight =
      new ExactSimilarityWeight(searcher)

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

    val bodyFieldType = new FieldType()
    bodyFieldType.setIndexOptions(IndexOptions.DOCS)
    bodyFieldType.setStored(true)

    val docWithBody = new Document
    docWithBody.add(new Field("id", "1", idFieldType))
    docWithBody.add(new BinaryDocValuesField("body", new BytesRef("foo".getBytes)))

    val docNoBody = new Document
    docNoBody.add(new Field("id", "2", idFieldType))

    ixWriter.addDocuments(Seq(docWithBody, docNoBody).asJava)
    ixWriter.forceMerge(1)
    ixWriter.commit()
    ixWriter.close()

    val ixReader = DirectoryReader.open(ixDir)
    val ixSearcher = new IndexSearcher(ixReader)
    val topDocs = ixSearcher.search(new ExactSimilarityQuery("body", SIMILARITY_JACCARD), 10)
    // val topDocs = ixSearcher.search(new DocValuesFieldExistsQuery("body"), 10)

    println(s"Found ${topDocs.scoreDocs.length} docs")
    topDocs.scoreDocs.foreach(d => println(s"${d.doc}, ${d.score}, ${ixSearcher.doc(d.doc)}"))

  }

}
