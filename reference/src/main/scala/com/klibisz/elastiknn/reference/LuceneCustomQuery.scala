package com.klibisz.elastiknn.reference

import java.io.File
import java.util

import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.codecs.simpletext.SimpleTextCodec
import org.apache.lucene.document.{Document, Field, FieldType, StoredField}
import org.apache.lucene.index.{DirectoryReader, IndexOptions, IndexWriter, IndexWriterConfig, LeafReaderContext, Term}
import org.apache.lucene.search.{DocIdSetIterator, Explanation, IndexSearcher, Query, ScoreMode, Scorer, TermQuery, Weight}
import org.apache.lucene.store.MMapDirectory

object LuceneCustomQuery {

  class BackwardsTermQuery(field: String, term: String) extends Query {

    private val fwdTerm = new Term(field, term)
    private val fwdQuery = new TermQuery(fwdTerm)
    private val bwdTerm = new Term(field, term.reverse)
    private val bwdQuery = new TermQuery(bwdTerm)

    class BackwardsWeight(indexSearcher: IndexSearcher) extends Weight(this) {

      private val bwdWeight = bwdQuery.createWeight(indexSearcher, ScoreMode.COMPLETE, 1f)
      private val fwdWeight = fwdQuery.createWeight(indexSearcher, ScoreMode.COMPLETE, 1f)

      override def extractTerms(terms: util.Set[Term]): Unit = {
        bwdWeight.extractTerms(terms)
        fwdWeight.extractTerms(terms)
      }

      override def explain(context: LeafReaderContext, doc: Int): Explanation = {
        val s: BackwardsScorer = backwardsScorer(context)
        s.iterator().advance(doc)
        s.explain()
      }

      private def backwardsScorer(context: LeafReaderContext): BackwardsScorer = {
        val bwdScorer = bwdWeight.scorer(context)
        val fwdScorer = fwdWeight.scorer(context)
        val bwdIter = if (bwdScorer != null) bwdScorer.iterator() else DocIdSetIterator.empty()
        val fwdIter = if (fwdScorer != null) fwdScorer.iterator() else DocIdSetIterator.empty()
        new BackwardsScorer(this, context, bwdIter, fwdIter)
      }

      override def scorer(context: LeafReaderContext): Scorer = backwardsScorer(context)

      override def isCacheable(ctx: LeafReaderContext): Boolean = false
    }

    override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = new BackwardsWeight(searcher)

    override def equals(other: Any): Boolean =
      other match {
        case otherq: BackwardsTermQuery => otherq.bwdQuery.equals(bwdQuery) && otherq.fwdQuery.equals(fwdQuery)
        case _                          => false
      }

    override def hashCode(): Int = ((classHash() * 31) + bwdQuery.hashCode() * 31) + fwdQuery.hashCode() * 31

    override def toString(field: String): String = s"BackwardsTermQuery for field $field, term $term"
  }

  class BackwardsScorer(weight: Weight, context: LeafReaderContext, bwdIter: DocIdSetIterator, fwdIter: DocIdSetIterator)
      extends Scorer(weight) {

    private val iter = new Iterator(bwdIter, fwdIter)
    private val bwdScore = 5f
    private val fwdScore = 1f
    private var currScore = 0f

    override def iterator(): DocIdSetIterator = iter

    override def getMaxScore(upTo: Int): Float = fwdScore // TODO: huh?

    override def score(): Float =
      if (docID() == bwdIter.docID()) bwdScore
      else if (docID() == fwdIter.docID()) fwdScore
      else 0f

    override def docID(): Int = iter.docID()

    final def explain(): Explanation =
      if (docID() == bwdIter.docID()) Explanation.`match`(bwdScore, s"Backwards term match ${this.getWeight.getQuery}")
      else if (docID() == fwdIter.docID()) Explanation.`match`(fwdScore, s"Forward term match ${this.getWeight.getQuery}")
      else null

  }

  class Iterator(bwdIter: DocIdSetIterator, fwdIter: DocIdSetIterator) extends DocIdSetIterator {

    override def docID(): Int = {
      val bid = bwdIter.docID()
      val fid = fwdIter.docID()
      if (bid <= fid && bid != DocIdSetIterator.NO_MORE_DOCS) bid
      else if (fid != DocIdSetIterator.NO_MORE_DOCS) fid
      else DocIdSetIterator.NO_MORE_DOCS
    }

    override def nextDoc(): Int = {
      val currDocId = docID()
      if (currDocId == bwdIter.docID()) bwdIter.nextDoc()
      if (currDocId == fwdIter.docID()) fwdIter.nextDoc()
      docID()
    }

    override def advance(target: Int): Int = {
      bwdIter.advance(target)
      fwdIter.advance(target)
      docID()
    }

    override def cost(): Long = 1L
  }

  def main(args: Array[String]): Unit = {

    val tmpDir = new File(s"/tmp/lucene-backward-${System.currentTimeMillis()}")
    val ixDir = new MMapDirectory(tmpDir.toPath)

    val ixWriterCfg = new IndexWriterConfig(new WhitespaceAnalyzer).setCodec(new SimpleTextCodec)
    val ixWriter = new IndexWriter(ixDir, ixWriterCfg)

    val idFieldType = new FieldType()
    idFieldType.setStored(true)
    idFieldType.setIndexOptions(IndexOptions.DOCS)
    idFieldType.setTokenized(false)

    val bodyFieldType = new FieldType()
    bodyFieldType.setIndexOptions(IndexOptions.DOCS)

    Seq(
      "how now brown cow",
      "cow".reverse,
      "brown".reverse,
      "how now brown cow".reverse,
      "brown brown cow".reverse
    ).zipWithIndex.foreach {
      case (body, id) =>
        val doc = new Document
        doc.add(new Field("id", id.toString, idFieldType))
        doc.add(new Field("body", body, bodyFieldType))
        doc.add(new StoredField("body", body))
        ixWriter.addDocument(doc)
    }

    ixWriter.forceMerge(1)
    ixWriter.commit()
    ixWriter.close()

    val ixReader = DirectoryReader.open(ixDir)
    val ixSearcher = new IndexSearcher(ixReader)

    val btq = new BackwardsTermQuery("body", "brown")
    val topDocs = ixSearcher.search(btq, 10)
    println(topDocs.scoreDocs.length)
    topDocs.scoreDocs.foreach(d => println(s"${d.doc}, ${d.score}, ${ixSearcher.doc(d.doc)}"))

  }

}
