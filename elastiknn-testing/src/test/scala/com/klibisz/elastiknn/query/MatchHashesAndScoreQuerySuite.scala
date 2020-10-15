package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.HashAndFreq
import com.klibisz.elastiknn.storage.UnsafeSerialization._
import com.klibisz.elastiknn.testing.LuceneSupport
import org.apache.lucene.document.{Document, Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.search.{IndexSearcher, MatchHashesAndScoreQuery, TermQuery}
import org.scalatest._

import scala.collection.mutable.ArrayBuffer

class MatchHashesAndScoreQuerySuite extends FunSuite with Matchers with LuceneSupport {

  val ft: VectorMapper.FieldType = new VectorMapper.FieldType("elastiknn_dense_float_vector")

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
          1f,
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
          1f,
          r,
          (_: LeafReaderContext) => (_: Int, numMatchingHashes: Int) => numMatchingHashes * 1f
        )
        val dd = s.search(q, 10)
        dd.scoreDocs should have length 2
        dd.scoreDocs.map(_.score) shouldBe Array(3f, 1f)
    }
  }

  test("documents with 0 matches are not candidates") {
    indexAndSearch() { w =>
      for (_ <- 0 until 10) {
        val d = new Document()
        Array(1, 2, 3, 4, 5).foreach(i => d.add(new Field("vec", writeInt(i), ft)))
        w.addDocument(d)
      }
    } {
      case (r, s) =>
        val hashes = Array(6, 7, 8, 9, 10).map(i => HashAndFreq.once(writeInt(i)))
        val q = new MatchHashesAndScoreQuery("vec", hashes, 5, 1f, r, (_: LeafReaderContext) => (_: Int, m: Int) => m * 1f)
        val dd = s.search(q, 10)
        dd.scoreDocs shouldBe empty
    }
  }

  test("returns no more than `candidates` doc IDs") {
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
        arr.foreach(i => d.add(new Field("vec", writeInt(i), ft)))
        w.addDocument(d)
      }
    } {
      case (r, s) =>
        val counts = ArrayBuffer.empty[Int]
        val q = new MatchHashesAndScoreQuery("vec",
                                             query,
                                             candidates,
                                             1f,
                                             r,
                                             (_: LeafReaderContext) =>
                                               (_: Int, c: Int) => {
                                                 counts.append(c)
                                                 c.toFloat
                                             })
        val dd = s.search(q, 10)
        dd.scoreDocs.length shouldBe 5
        counts.toVector.sorted shouldBe Vector(1, 1, 2, 2, 2)
    }
  }

  test("Stops early after limit is exceeded") {

    indexAndSearch() { w =>
      (0 until 100)
        .map { i =>
          Array(1, 2, 3, i)
        }
        .foreach { arr =>
          val d = new Document()
          arr.foreach(i => d.add(new Field("vec", writeInt(i), ft)))
          w.addDocument(d)
        }
    } {
      case (r, s) =>
        val b = new ArrayBuffer[Int]()
        val v = Array(1, 2, 3).map(i => HashAndFreq.once(writeInt(i)))
        val q = new MatchHashesAndScoreQuery("vec",
                                             v,
                                             20,
                                             0.1f,
                                             r,
                                             (_: LeafReaderContext) =>
                                               (id: Int, _: Int) => {
                                                 b.append(id)
                                                 id.toFloat
                                             })
        val dd = s.search(q, 20)
        b.toArray.length shouldBe 10
        dd.scoreDocs.length shouldBe 10
    }

  }

}
