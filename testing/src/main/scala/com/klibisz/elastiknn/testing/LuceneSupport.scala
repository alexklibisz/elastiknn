package com.klibisz.elastiknn.testing

import java.nio.file.Files

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.codecs.Codec
import org.apache.lucene.codecs.lucene84.Lucene84Codec
import org.apache.lucene.index._
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.MMapDirectory

trait LuceneSupport {

  def indexAndSearch[I, S](codec: Codec = new Lucene84Codec(), analyzer: Analyzer)(index: IndexWriter => I)(
      search: (IndexReader, IndexSearcher) => S): Unit = {
    val tmpDir = Files.createTempDirectory(null).toFile
    val indexDir = new MMapDirectory(tmpDir.toPath)
    val indexWriterCfg = new IndexWriterConfig(analyzer).setCodec(codec)
    val indexWriter = new IndexWriter(indexDir, indexWriterCfg)
    try {
      index(indexWriter)
      indexWriter.commit()
      indexWriter.forceMerge(1)
      val indexReader = DirectoryReader.open(indexDir)
      try search(indexReader, new IndexSearcher(indexReader))
      finally indexReader.close()
    } finally {
      indexWriter.close()
      tmpDir.delete()
    }
  }

}
