package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.HashAndFreq
import com.klibisz.elastiknn.storage.UnsafeSerialization._
import com.klibisz.elastiknn.testing.LuceneSupport
import org.apache.lucene.codecs.lucene84.Lucene84Codec
import org.apache.lucene.document.{Document, Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.search.{IndexSearcher, MatchHashesAndScoreQuery, TermQuery}
import org.apache.lucene.util.BytesRef
import org.scalatest._

class MatchHashesAndScoreQuerySuite extends FunSuite with Matchers with LuceneSupport {

  val ft = new VectorMapper.FieldType("elastiknn_dense_float_vector")

  test("empty harness") {
    indexAndSearch() { (_: IndexWriter) =>
      Assertions.succeed
    } { (_: IndexReader, _: IndexSearcher) =>
      Assertions.succeed
    }
  }

  test("minimal id example") {
    indexAndSearch() { w =>
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
    indexAndSearch() { w =>
      val d = new Document()
      d.add(new Field("vec", writeInt(42), ft))
      d.add(new Field("vec", writeInt(99), ft))
      w.addDocument(d)
    } {
      case (r, s) =>
        val hashes = Array(HashAndFreq.once(writeInt(42)), HashAndFreq.once(writeInt(99)), HashAndFreq.once(writeInt(22)))
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
    indexAndSearch() { w =>
      val (d1, d2) = (new Document(), new Document())
      Array(3, 3, 3, 8, 8, 7).foreach(i => d1.add(new Field("vec", writeInt(i), ft)))
      Array(9, 9, 9, 6, 6, 1).foreach(i => d2.add(new Field("vec", writeInt(i), ft)))
      w.addDocument(d1)
      w.addDocument(d2)
    } {
      case (r, s) =>
        val hashes = Array(3, 3, 3, 0, 0, 6).map(i => HashAndFreq.once(writeInt(i)))
        val q = new MatchHashesAndScoreQuery(
          "vec",
          hashes,
          10,
          r,
          (_: LeafReaderContext) => (_: Int, numMatchingHashes: Int) => numMatchingHashes * 1f
        )
        val dd = s.search(q, 10)
        dd.scoreDocs should have length 2
        dd.scoreDocs.map(_.score) shouldBe Array(3f, 1f)
    }
  }

  test("no matches") {
    indexAndSearch() { w =>
      val d1 = new Document()
      Array(1, 2, 3, 4, 5).foreach(i => d1.add(new Field("vec", writeInt(i), ft)))
      w.addDocument(d1)
    } {
      case (r, s) =>
        val hashes = Array(6, 7, 8, 9, 10).map(i => HashAndFreq.once(writeInt(i)))
        val q = new MatchHashesAndScoreQuery("vec", hashes, 10, r, (_: LeafReaderContext) => (_: Int, m: Int) => m * 1f)
        val dd = s.search(q, 10)
        dd.scoreDocs should have length 1
        dd.scoreDocs.map(_.score) shouldBe Array(0f)
    }
  }

}
