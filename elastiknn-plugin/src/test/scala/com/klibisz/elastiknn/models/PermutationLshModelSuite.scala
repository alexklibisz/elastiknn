package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.lucene.{HashFieldType, LuceneSupport}
import com.klibisz.elastiknn.query.HashingQuery
import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt
import org.apache.lucene.document.{Document, Field}
import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.MatchHashesAndScoreQuery
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class PermutationLshModelSuite extends AnyFunSuite with Matchers with LuceneSupport {

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
        hd.foreach(h => d.add(new Field("vec", h.hash, HashFieldType.HASH_FIELD_TYPE)))
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

  test("deterministic lucene indexing and queries") {
    // Re-index the same set of docs several times and run the same queries on each index.
    // The results from each repetition should be identical to all other repetitions.
    implicit val rng: Random = new Random(0)
    val corpusVecs = Vec.DenseFloat.randoms(1024, 1000, unit = true)
    val queryVecs = Vec.DenseFloat.randoms(1024, 100, unit = true)
    val lsh = new PermutationLshModel(128, true)

    // Several repetitions[several queries[several results per query[each result is a (docId, score)]]].
    val repeatedResults: Seq[Vector[Vector[(Int, Float)]]] = (0 until 3).map { _ =>
      val (_, queryResults) = indexAndSearch() { w =>
        corpusVecs.foreach { v =>
          val d = new Document()
          HashingQuery.index("vec", HashFieldType.HASH_FIELD_TYPE, v, lsh.hash(v.values)).foreach(d.add)
          w.addDocument(d)
        }
      } {
        case (r, s) =>
          queryVecs.map { v =>
            val q = new HashingQuery("vec", v, 200, lsh.hash(v.values), ExactSimilarityFunction.Cosine)
            s.search(q.toLuceneQuery(r), 100).scoreDocs.map(sd => (sd.doc, sd.score)).toVector
          }
      }
      queryResults
    }
    val distinct = repeatedResults.distinct
    distinct.length shouldBe 1
  }
}
