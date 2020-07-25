package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.storage.UnsafeSerialization._
import com.klibisz.elastiknn.testing.LuceneHarness
import org.apache.lucene.codecs.lucene84.Lucene84Codec
import org.apache.lucene.document.{Document, Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.search.{IndexSearcher, MatchHashesAndScoreQuery, TermQuery}
import org.apache.lucene.util.BytesRef
import org.scalatest._

class MatchHashesAndScoreQuerySuite extends FunSuite with Matchers with LuceneHarness {

  val vecFieldType = new VectorMapper.FieldType("elastiknn_dense_float_vector")

  def indexAndSearch[W, R]: (IndexWriter => W) => ((IndexReader, IndexSearcher) => R) => Unit =
    indexAndSearch(new Lucene84Codec(), vecFieldType.indexAnalyzer.analyzer)

  test("empty harness") {
    indexAndSearch { (_: IndexWriter) =>
      Assertions.succeed
    } { (_: IndexReader, _: IndexSearcher) =>
      Assertions.succeed
    }
  }

  test("minimal id example") {
    indexAndSearch { w =>
      val d = new Document()
      val ft = new FieldType()
      ft.setIndexOptions(IndexOptions.DOCS)
      d.add(new Field("id", "foo", ft))
      d.add(new Field("id", "bar", ft))
      w.addDocument(d)
    } {
      case (_, s) =>
        val q = new TermQuery(new Term("id", "foo"))
        val dd = s.search(q, 10)
        dd.scoreDocs should have length 1
    }
  }

  test("no repeating values") {
    indexAndSearch { w =>
      val d = new Document()
      d.add(new Field("vec", writeInt(42), vecFieldType))
      d.add(new Field("vec", writeInt(99), vecFieldType))
      w.addDocument(d)
    } {
      case (r, s) =>
        val hashes = Array(new BytesRef(writeInt(42)), new BytesRef(writeInt(99)), new BytesRef(writeInt(22)))
        val q = new MatchHashesAndScoreQuery(
          "vec",
          hashes,
          10,
          r,
          (_: LeafReaderContext) =>
            (docId: Int, numMatchingHashes: Int) => {
              docId shouldBe 0
              numMatchingHashes shouldBe 2
              99d
          }
        )
        val dd = s.search(q, 10)
        dd.scoreDocs should have length 1
        dd.scoreDocs.head.score shouldBe 99f
        dd.scoreDocs.head.doc shouldBe 0
        dd.scoreDocs.head.shardIndex shouldBe 0
    }
  }

  test("repeating terms") {
    indexAndSearch { w =>
      val (d1, d2) = (new Document(), new Document())
      Array(3, 3, 3, 8, 8, 7).foreach(i => d1.add(new Field("vec", writeInt(i), vecFieldType)))
      Array(9, 9, 9, 6, 6, 1).foreach(i => d2.add(new Field("vec", writeInt(i), vecFieldType)))
      w.addDocument(d1)
      w.addDocument(d2)
    } {
      case (r, s) =>
        val hashes = Array(3, 3, 3, 0, 0, 6).map(i => new BytesRef(writeInt(i)))
        val q = new MatchHashesAndScoreQuery(
          "vec",
          hashes,
          10,
          r,
          (_: LeafReaderContext) => (_: Int, numMatchingHashes: Int) => numMatchingHashes * 1f
        )
        val dd = s.search(q, 10)
        dd.scoreDocs should have length 2
        dd.scoreDocs.map(_.score) shouldBe Array(3f, 2f)
    }
  }

}
