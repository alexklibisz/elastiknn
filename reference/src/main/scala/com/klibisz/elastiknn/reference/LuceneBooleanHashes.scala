package com.klibisz.elastiknn.reference

import java.io.File

import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{Document, Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.search.{BooleanClause, BooleanQuery, IndexSearcher, TermQuery}
import org.apache.lucene.store.MMapDirectory

object LuceneBooleanHashes {

  // Goal: get the term query to return docs with matching hashes.

  def main(args: Array[String]): Unit = {

    val tmpDir = new File(s"/tmp/lucene-${System.currentTimeMillis()}")
    println(tmpDir)
    val ixDir = new MMapDirectory(tmpDir.toPath)
    val ixOpts = IndexOptions.DOCS

    val iwc = new IndexWriterConfig(new WhitespaceAnalyzer)
    val iw = new IndexWriter(ixDir, iwc)

    val idFieldType = new FieldType()
    idFieldType.setStored(true)
    idFieldType.setIndexOptions(ixOpts)
    idFieldType.setTokenized(false)

    val hashesFieldType = new FieldType()
    hashesFieldType.setStored(false) // Don't need to store the hashes.
    hashesFieldType.setIndexOptions(ixOpts)
    hashesFieldType.setTokenized(false)
    hashesFieldType.setOmitNorms(true)

    val doc1 = new Document()
    doc1.add(new Field("id", "1", idFieldType))
    Seq("123", "456", "789").foreach(t => doc1.add(new Field("hashes", t, hashesFieldType)))
    iw.addDocument(doc1)
    iw.commit()

    val doc2 = new Document()
    doc2.add(new Field("id", "2", idFieldType))
    Seq("111", "222", "789").foreach(t => doc2.add(new Field("hashes", t, hashesFieldType)))
    iw.addDocument(doc2)
    iw.commit()

    val doc3 = new Document()
    doc3.add(new Field("id", "3", idFieldType))
    doc3.add(new Field("hashes", "999", hashesFieldType))
    iw.addDocument(doc3)
    iw.commit()

    val ixReader = DirectoryReader.open(ixDir)
    val ixSearcher = new IndexSearcher(ixReader)

    val boolQueryBuilder = new BooleanQuery.Builder
    val boolQuery = boolQueryBuilder
      .add(new BooleanClause(new TermQuery(new Term("hashes", "222")), BooleanClause.Occur.SHOULD))
      .add(new BooleanClause(new TermQuery(new Term("hashes", "789")), BooleanClause.Occur.SHOULD))
      .setMinimumNumberShouldMatch(1)
      .build()

    val topDocs = ixSearcher.search(boolQuery, 10)
    println(topDocs.scoreDocs.length)
    topDocs.scoreDocs.foreach(d => println(s"${d.doc}, ${d.score}, ${ixSearcher.doc(d.doc)}"))

  }

}
