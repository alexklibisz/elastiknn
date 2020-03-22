package com.klibisz.elastiknn.reference.lucene

import java.io.File

import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.codecs.simpletext.SimpleTextCodec
import org.apache.lucene.document.{Document, Field, FieldType, StoredField}
import org.apache.lucene.index._
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.search.{BooleanClause, BooleanQuery, IndexSearcher, TermQuery}
import org.apache.lucene.store.MMapDirectory

object BooleanHashes {

  // Goal: the query score should be the number of intersected temrs.
  // After this runs, you can look cat the .pst file in the tmpDir to see the inverted index.

  def main(args: Array[String]): Unit = {

    val tmpDir = new File(s"/tmp/lucene-${System.currentTimeMillis()}")
    println(tmpDir)
    val ixDir = new MMapDirectory(tmpDir.toPath)

    val ixWriterCfg = new IndexWriterConfig(new WhitespaceAnalyzer).setCodec(new SimpleTextCodec)
    val ixWriter = new IndexWriter(ixDir, ixWriterCfg)

    val idFieldType = new FieldType()
    idFieldType.setStored(true)
    idFieldType.setIndexOptions(IndexOptions.DOCS)
    idFieldType.setTokenized(false)

    val hashesFieldType = new FieldType()
    // Only store the document ids, not frequencies, offsets, etc.
    hashesFieldType.setIndexOptions(IndexOptions.DOCS)

    // Not sure if all of these are necessary.
    hashesFieldType.setOmitNorms(true)
    hashesFieldType.setTokenized(false)
    hashesFieldType.setStored(false) // Don't need to store the hashes.
    hashesFieldType.setStoreTermVectors(false)
    hashesFieldType.setStoreTermVectorPayloads(false)
    hashesFieldType.setStoreTermVectorPositions(false)

    val doc1 = new Document()
    doc1.add(new Field("id", "1", idFieldType))
    Seq("123", "456", "789").foreach(t => doc1.add(new Field("hashes", t, hashesFieldType)))
    doc1.add(new StoredField("stored_body", "blah blah"))
    ixWriter.addDocument(doc1)
    ixWriter.commit()

    val doc2 = new Document()
    doc2.add(new Field("id", "2", idFieldType))
    Seq("111", "222", "789").foreach(t => doc2.add(new Field("hashes", t, hashesFieldType)))
    doc2.add(new StoredField("stored_body", "blah blah"))
    ixWriter.addDocument(doc2)
    ixWriter.commit()

    val doc3 = new Document()
    doc3.add(new Field("id", "3", idFieldType))
    doc3.add(new Field("hashes", "999", hashesFieldType))
    doc3.add(new StoredField("stored_body", "blah blah"))
    ixWriter.addDocument(doc3)

    ixWriter.commit()
    ixWriter.forceMerge(1) // Have to do this to get the .pst file.
    ixWriter.close()

    val ixReader = DirectoryReader.open(ixDir)
    val ixSearcher = new IndexSearcher(ixReader)
    // This is the magic setting to get the score to be the number of intersections instead of a fancier formula.
    ixSearcher.setSimilarity(new BooleanSimilarity)

    val boolQueryBuilder = new BooleanQuery.Builder
    val boolQuery = boolQueryBuilder
      .add(new BooleanClause(new TermQuery(new Term("hashes", "222")), BooleanClause.Occur.SHOULD))
      .add(new BooleanClause(new TermQuery(new Term("hashes", "789")), BooleanClause.Occur.SHOULD))
      .setMinimumNumberShouldMatch(1)
      .build()

    val topDocs = ixSearcher.search(boolQuery, 10)
    println(topDocs.scoreDocs.length)
    topDocs.scoreDocs.foreach(d => println(s"${d.doc}, ${d.score}, ${ixSearcher.doc(d.doc)}"))

    import scala.sys.process._
    println(Seq("/bin/sh", "-c", s"cat ${tmpDir}/*.pst").!!)
  }

}
