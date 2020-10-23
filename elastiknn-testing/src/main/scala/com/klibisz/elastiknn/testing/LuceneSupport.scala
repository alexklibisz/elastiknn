package com.klibisz.elastiknn.testing

import java.nio.file.Files

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.codecs.Codec
import org.apache.lucene.codecs.lucene86.Lucene86Codec
import org.apache.lucene.index._
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.MMapDirectory
import org.elasticsearch.common.lucene.Lucene

trait LuceneSupport {

  def indexAndSearch[I, S](codec: Codec = new Lucene86Codec(), analyzer: Analyzer = Lucene.KEYWORD_ANALYZER)(index: IndexWriter => I)(
      search: (IndexReader, IndexSearcher) => S): (I, S) = {
    val tmpDir = Files.createTempDirectory(null).toFile
    val indexDir = new MMapDirectory(tmpDir.toPath)
    val indexWriterCfg = new IndexWriterConfig(analyzer).setCodec(codec)
    val indexWriter = new IndexWriter(indexDir, indexWriterCfg)
    val res = try {
      val ires = index(indexWriter)
      indexWriter.commit()
      indexWriter.forceMerge(1)
      indexWriter.close()
      val indexReader = DirectoryReader.open(indexDir)
      val sres = try search(indexReader, new IndexSearcher(indexReader))
      finally indexReader.close()
      (ires, sres)
    } finally {
      indexWriter.close()
      tmpDir.delete()
    }
    res
  }

}
