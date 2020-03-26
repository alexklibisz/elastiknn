package com.klibisz.elastiknn.reference.lucene

import java.io.File
import java.util
import java.util.Objects

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
import com.klibisz.elastiknn.models.{ExactSimilarityModelOld, ExactSimilarityScore, JaccardLshModel}
import com.klibisz.elastiknn.utils.SparseBoolVectorUtils
import com.klibisz.elastiknn.{ElastiKnnVector, JaccardLshModelOptions, SparseBoolVector}
import org.apache.lucene.codecs.simpletext.SimpleTextCodec
import org.apache.lucene.document.{Document, Field, FieldType, StoredField}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.util.BytesRef

import scala.util.Random

object JaccardLshQuery extends SparseBoolVectorUtils {

  object JaccardLshQuery {

    def fieldHashes(field: String): String = s"$field.hashes"
    def fieldVector(field: String): String = s"$field.vector"

    private val hashesFieldType: FieldType = {
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

    def document(field: String, sbv: SparseBoolVector, modelOptions: JaccardLshModelOptions): Document = {
      val model = new JaccardLshModel(modelOptions.seed, modelOptions.numBands, modelOptions.numRows)
      val hashTerms: Array[Int] = model.hash(sbv.trueIndices)
      val doc = new Document
      doc.add(new StoredField(fieldVector(field), sbv.toByteArray))
      hashTerms.foreach { h =>
        doc.add(new Field(fieldHashes(field), intToBytes(h), hashesFieldType))
      }
      doc
    }

  }

  class JaccardLshQuery(val field: String, val queryVector: SparseBoolVector, val modelOptions: JaccardLshModelOptions, candidates: Int)
      extends Query {

    import JaccardLshQuery._

    private val booleanIntersectionQuery: BooleanQuery = {
      val bqb = new BooleanQuery.Builder
      val model = new JaccardLshModel(modelOptions.seed, modelOptions.numBands, modelOptions.numRows)
      model.hash(queryVector.trueIndices).foreach { h =>
        bqb.add(new BooleanClause(new TermQuery(new Term(fieldHashes(field), intToBytes(h))), BooleanClause.Occur.SHOULD))
      }
      bqb.build()
    }

    override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight =
      new JaccardLshWeight(searcher)

    class JaccardLshWeight(searcher: IndexSearcher) extends Weight(this) {

      searcher.setSimilarity(new BooleanSimilarity)

      private val booleanWeight = booleanIntersectionQuery.createWeight(searcher, ScoreMode.TOP_SCORES, 1f)

      override def extractTerms(terms: util.Set[Term]): Unit = ()

      override def explain(context: LeafReaderContext, doc: Int): Explanation = ???

      override def scorer(context: LeafReaderContext): Scorer =
        new JaccardLshScorer(this, searcher, context, booleanWeight.scorer(context))

      override def isCacheable(ctx: LeafReaderContext): Boolean = false
    }

    class JaccardLshScorer(weight: Weight, searcher: IndexSearcher, context: LeafReaderContext, booleanScorer: Scorer)
        extends Scorer(weight) {

      private val scoreHeap: MinMaxPriorityQueue[java.lang.Integer] = MinMaxPriorityQueue.create[java.lang.Integer]()

      override val iterator: DocIdSetIterator = if (booleanScorer != null) booleanScorer.iterator() else DocIdSetIterator.empty()

      override def getMaxScore(upTo: Int): Float = Float.MaxValue

      override def score(): Float = {
        val intersection = booleanScorer.score()
        val computeExact: Boolean = if (scoreHeap.size() < candidates) {
          scoreHeap.add(intersection.toInt)
          true
        } else if (intersection > scoreHeap.peekFirst()) {
          scoreHeap.removeFirst()
          scoreHeap.add(intersection.toInt)
          true
        } else false

        if (computeExact) {
          val docId = iterator.docID()
          println(s"Compute exact score for doc with id $docId, intersection score $intersection")
          val doc = searcher.doc(docId)
          val ekvBytes = doc.getField(fieldVector(field)).binaryValue.bytes
          val ekv = SparseBoolVector.parseFrom(ekvBytes.take(ekvBytes.length))
          val ExactSimilarityScore(score, _) = ExactSimilarityModelOld
            .apply(
              SIMILARITY_JACCARD,
              ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(queryVector)),
              ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(ekv))
            )
            .get
          score.toFloat
        } else 0f
      }

      override def docID(): Int = iterator.docID()
    }

    override def toString(field: String): String = s"$field:$queryVector:$modelOptions"

    override def equals(other: Any): Boolean = other match {
      case jlq: JaccardLshQuery => jlq.queryVector == queryVector && jlq.modelOptions == modelOptions && jlq.field == field
      case _                    => false
    }

    override def hashCode(): Int = Objects.hash(field, queryVector, modelOptions)

  }

  def main(args: Array[String]): Unit = {
    val tmpDir = new File(s"/tmp/lucene-${System.currentTimeMillis()}")
    val ixDir = new MMapDirectory(tmpDir.toPath)

    val ixWriterCfg = new IndexWriterConfig().setCodec(new SimpleTextCodec)
    val ixWriter = new IndexWriter(ixDir, ixWriterCfg)

    val idFieldType = new FieldType()
    idFieldType.setStored(true)
    idFieldType.setIndexOptions(IndexOptions.DOCS)

    val field = "vec"

    implicit val rng: Random = new Random(0)
    val sbvs = SparseBoolVector.randoms(20, 25)
    val queryVector = sbvs.head
    val modelOptions = JaccardLshModelOptions(1, "", 11, 1)

    sbvs.zipWithIndex.foreach {
      case (sbv, i) =>
        val doc = JaccardLshQuery.document(field, sbv, modelOptions)
        doc.add(new Field("id", i.toString, idFieldType))
        ixWriter.addDocument(doc)
    }

    ixWriter.forceMerge(1)
    ixWriter.commit()
    ixWriter.close()

    val ixReader = DirectoryReader.open(ixDir)
    val ixSearcher = new IndexSearcher(ixReader)

    val topDocs = ixSearcher.search(new JaccardLshQuery(field, queryVector, modelOptions, 30), 10)
    println(s"Found ${topDocs.scoreDocs.length} docs")
    topDocs.scoreDocs.foreach(d => println(s"${d.doc}, ${d.score}, ${ixSearcher.doc(d.doc)}"))
  }

}
