package org.apache.lucene.search

import com.klibisz.elastiknn.lucene.{HashFieldType, LuceneSupport}
import com.klibisz.elastiknn.models.HashAndFreq
import com.klibisz.elastiknn.storage.ByteBufferSerialization._
import org.apache.lucene.document.{Document, Field, FieldType}
import org.apache.lucene.index._
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer

class MatchHashesAndScoreQuerySuite extends AnyFunSuite with Matchers with LuceneSupport {

  test("empty harness") {
    indexAndSearch() { _ => Assertions.succeed } { (_: IndexReader, _: IndexSearcher) => Assertions.succeed }
  }

  test("minimal id example") {
    indexAndSearch() { w =>
      val d = new Document()
      val ft = new FieldType()
      ft.setIndexOptions(IndexOptions.DOCS)
      d.add(new Field("id", "foo", ft))
      d.add(new Field("id", "bar", ft))
      w.addDocument(d)
    } { case (_, s) =>
      val q = new TermQuery(new Term("id", "foo"))
      val dd = s.search(q, 10)
      dd.scoreDocs should have length 1
    }
  }

  test("no repeating values") {
    indexAndSearch() { w =>
      val d = new Document()
      d.add(new Field("vec", writeInt(42), HashFieldType.HASH_FIELD_TYPE))
      d.add(new Field("vec", writeInt(99), HashFieldType.HASH_FIELD_TYPE))
      w.addDocument(d)
    } { case (r, s) =>
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
      dd.scoreDocs.head.shardIndex shouldBe -1
    }
  }

  test("repeating terms") {
    indexAndSearch() { w =>
      val (d1, d2) = (new Document(), new Document())
      Array(3, 3, 3, 8, 8, 7).foreach(i => d1.add(new Field("vec", writeInt(i), HashFieldType.HASH_FIELD_TYPE)))
      Array(9, 9, 9, 6, 6, 1).foreach(i => d2.add(new Field("vec", writeInt(i), HashFieldType.HASH_FIELD_TYPE)))
      w.addDocument(d1)
      w.addDocument(d2)
    } { case (r, s) =>
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
      val ex0 = s.explain(q, 0)
      ex0.isMatch shouldBe true
      ex0.getValue.doubleValue() shouldBe 3d
      ex0.getDescription shouldBe f"Document [0] and the query vector share [3] of [6] hashes. Their exact similarity score is [${3d}%f]."
      val ex1 = s.explain(q, 1)
      ex1.isMatch shouldBe true
      ex1.getValue.doubleValue() shouldBe 1d
      ex1.getDescription shouldBe f"Document [1] and the query vector share [1] of [6] hashes. Their exact similarity score is [${1d}%f]."
    }
  }

  test("documents with 0 matches are not candidates") {
    indexAndSearch() { w =>
      for (_ <- 0 until 10) {
        val d = new Document()
        Array(1, 2, 3, 4, 5).foreach(i => d.add(new Field("vec", writeInt(i), HashFieldType.HASH_FIELD_TYPE)))
        w.addDocument(d)
      }
    } { case (r, s) =>
      val hashes = Array(6, 7, 8, 9, 10).map(i => HashAndFreq.once(writeInt(i)))
      val q = new MatchHashesAndScoreQuery("vec", hashes, 5, r, (_: LeafReaderContext) => (_: Int, m: Int) => m * 1f)
      val dd = s.search(q, 10)
      dd.scoreDocs shouldBe empty
      val ex = s.explain(q, 0)
      ex.isMatch shouldBe false
      ex.getDescription shouldBe "Document [0] and the query vector share no common hashes."
    }
  }

  ignore("returns no candidates with zero hash matches") {
    // TODO!
    succeed
  }

  ignore("returns no more than `candidates` doc IDs") {
    val query = Array(6, 7).map(i => HashAndFreq.once(writeInt(i)))
    val candidates = 5

    // Three docs with count > 1, three with count = 1, one with count = 0.
    // This will make the min candidate count be 1. There are 6 docs with count = 1, but the query should only
    // return 5 candidates.

    indexAndSearch() { w =>
      Seq(
        Array(6, 7),
        Array(1, 7),
        Array(6, 7),
        Array(1, 7),
        Array(6, 7),
        Array(1, 7),
        Array(1, 2)
      ).foreach { arr =>
        val d = new Document()
        arr.foreach(i => d.add(new Field("vec", writeInt(i), HashFieldType.HASH_FIELD_TYPE)))
        w.addDocument(d)
      }
    } { case (r, s) =>
      val counts = ArrayBuffer.empty[Int]
      val q = new MatchHashesAndScoreQuery(
        "vec",
        query,
        candidates,
        r,
        (_: LeafReaderContext) =>
          (_: Int, c: Int) => {
            counts.append(c)
            c.toFloat
          }
      )
      val dd = s.search(q, 10)
      dd.scoreDocs.length shouldBe 5
      counts.toVector.sorted shouldBe Vector(1, 1, 2, 2, 2)
    }
  }

}
