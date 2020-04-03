package com.klibisz.elastiknn.reference.lucene

import java.io.File

import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{Document, Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.search.{IndexSearcher, TermQuery}
import org.apache.lucene.store.MMapDirectory

object TermTags {

  def main(args: Array[String]): Unit = {

    val tmpDir = new File(s"/tmp/lucene-${System.currentTimeMillis()}")
    println(tmpDir)
    val ixDir = new MMapDirectory(tmpDir.toPath)
    val ixOpts = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
    val anz = new WhitespaceAnalyzer

    val iwc = new IndexWriterConfig(anz)
    val iw = new IndexWriter(ixDir, iwc)

    val tagsFieldType = new FieldType()
    tagsFieldType.setStored(true)
    tagsFieldType.setIndexOptions(ixOpts)

    val idFieldType = new FieldType()
    idFieldType.setStored(true)
    idFieldType.setIndexOptions(ixOpts)
    idFieldType.setTokenized(false)

    val doc1 = new Document()
    doc1.add(new Field("id", "1", idFieldType))
    doc1.add(new Field("tag", "star-trek captain-picard tv-show space", tagsFieldType))
    iw.addDocument(doc1)
    iw.commit()

    val doc2 = new Document()
    doc2.add(new Field("id", "2", idFieldType))
    doc2.add(new Field("tag", "star-wars darth-vader space", tagsFieldType))
    iw.addDocument(doc2)
    iw.commit()

    val doc3 = new Document()
    doc3.add(new Field("id", "3", idFieldType))
    doc3.add(new Field("tag", "comedy", tagsFieldType))
    iw.addDocument(doc3)
    iw.commit()

    val ixReader = DirectoryReader.open(ixDir)
    val ixSearcher = new IndexSearcher(ixReader)

    // Search for a doc with a "space" tag should only return the first two docs.
    val termQuery = new TermQuery(new Term("tag", "space"))
    val topDocs = ixSearcher.search(termQuery, 10)
    topDocs.scoreDocs.foreach(d => println(s"${d.doc}, ${d.score}, ${ixSearcher.doc(d.doc)}"))

  }

}
