package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.mapper
import com.klibisz.elastiknn.storage.UnsafeSerialization._
import com.klibisz.elastiknn.testing.LuceneSupport
import org.apache.lucene.document.{Document, Field}
import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.MatchHashesAndScoreQuery
import org.scalatest._

import scala.util.Random

class MagnitudesLshSuite extends FunSuite with Matchers with LuceneSupport {

  val ft = new mapper.VectorMapper.FieldType("elastiknn_dense_float_vector")

  test("example from paper") {
    val mapping = Mapping.MagnitudesLsh(6, 4)
    val mlsh = new MagnitudesLsh(mapping)
    val vec = Vec.DenseFloat(0.1f, -0.3f, -0.4f, 0, 0.2f)
    val hashes = mlsh(vec).map(h => (readInt(h.getHash), h.getFreq))
    hashes shouldBe Array((-3, 4), (-2, 3), (5, 2), (1, 1))
  }

  test("another example") {
    val mapping = Mapping.MagnitudesLsh(10, 4)
    val mlsh = new MagnitudesLsh(mapping)
    val vec = Vec.DenseFloat(10f, -2f, 0f, 99f, 0.1f, -8f, 42f, -13f, 6f, 0.1f)
    val hashes = mlsh(vec).map(h => (readInt(h.getHash), h.getFreq))
    // Get the top 4 indices by absolute value:   (4, 7, 8, 1)
    // Negate the ones with negative values:      (4, 7, -8, 1)
    // Repeat each one proportional to its rank:  (4, 4, 4, 4, 7, 7, 7, -8, -8, 1)
    hashes shouldBe Array((4, 4), (7, 3), (-8, 2), (1, 1))
  }

  test("ties") {
    // Since index 1 and 2 are tied, index 5 should have freq = 2 instead of 3.
    val mlsh = new MagnitudesLsh(Mapping.MagnitudesLsh(6, 4))
    val vec = Vec.DenseFloat(2f, 2f, 0f, 0f, 1f, 4f)
    val hashes = mlsh(vec).map(h => (readInt(h.getHash), h.getFreq))
    hashes.sorted shouldBe Array((6, 4), (1, 3), (2, 3), (5, 1)).sorted
  }

  test("deterministic") {
    implicit val rng: Random = new Random(0)
    val dims = 1024
    val mlsh = new MagnitudesLsh(Mapping.MagnitudesLsh(dims, 128))
    (0 until 100).foreach { _ =>
      val vec = Vec.DenseFloat.random(dims)
      val hashes = (0 until 100).map(_ => mlsh(vec).map(h => (readInt(h.getHash), h.getFreq)).mkString(","))
      hashes.distinct.length shouldBe 1
    }
  }

  test("lucene example where counting matters") {

    // This example demonstrates a tricky condition: 0 appears once in the query vector and three times in corpus vector
    // zero, so it should only contribute 1 to the score for corpus vector 0. Similarly, 4 appears twice in the query
    // vector and three times in corpus vector 1, so it should only contribute 2 to the socre for corpus vector 1.

    val hc0 = Array((0, 3), (2, 2), (4, 1)).map { case (n, c) => new HashAndFreq(writeInt(n), c) }
    val hc1 = Array((4, 3), (1, 2), (2, 1)).map { case (n, c) => new HashAndFreq(writeInt(n), c) }
    val hq = Array((2, 3), (4, 2), (0, 1)).map { case (n, c)  => new HashAndFreq(writeInt(n), c) }

    indexAndSearch() { w =>
      Seq(hc0, hc1).foreach { hd =>
        val d = new Document()
        hd.foreach(h => d.add(new Field("vec", h.getHash, ft)))
        w.addDocument(d)
      }
    } {
      case (reader, searcher) =>
        val f: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
          (_: LeafReaderContext) => (_: Int, matches: Int) => matches * 1f

        val q = new MatchHashesAndScoreQuery("vec", hq, 2, reader, f)
        val res = searcher.search(q, 2)
        res.scoreDocs.map(_.doc) shouldBe Array(0, 1)
        res.scoreDocs.map(_.score) shouldBe Array(3f, 2f)
    }

  }

}
