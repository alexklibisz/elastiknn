package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.mapper
import com.klibisz.elastiknn.query.HashingQuery
import com.klibisz.elastiknn.storage.UnsafeSerialization._
import com.klibisz.elastiknn.testing.LuceneSupport
import org.apache.lucene.document.{Document, Field}
import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.{MatchHashesAndScoreQuery}
import org.scalatest._

import scala.util.Random

class PermutationLshModelSuite extends FunSuite with Matchers with LuceneSupport {

  val ft = new mapper.VectorMapper.FieldType("elastiknn_dense_float_vector")

  test("example from paper") {
    val mapping = Mapping.PermutationLsh(6, 4, true)
    val mlsh = new PermutationLshModel(mapping.k, mapping.repeating)
    val vec = Vec.DenseFloat(0.1f, -0.3f, -0.4f, 0, 0.2f)
    val hashes = mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq))
    hashes shouldBe Array((-3, 4), (-2, 3), (5, 2), (1, 1))
  }

  test("example from paper without repetition") {
    val mapping = Mapping.PermutationLsh(6, 4, false)
    val mlsh = new PermutationLshModel(mapping.k, mapping.repeating)
    val vec = Vec.DenseFloat(0.1f, -0.3f, -0.4f, 0, 0.2f)
    val hashes = mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq))
    hashes shouldBe Array((-3, 1), (-2, 1), (5, 1), (1, 1))
  }

  test("another example") {
    val mapping = Mapping.PermutationLsh(10, 4, true)
    val mlsh = new PermutationLshModel(mapping.k, mapping.repeating)
    val vec = Vec.DenseFloat(10f, -2f, 0f, 99f, 0.1f, -8f, 42f, -13f, 6f, 0.1f)
    val hashes = mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq))
    // Get the top 4 indices by absolute value:   (4, 7, 8, 1)
    // Negate the ones with negative values:      (4, 7, -8, 1)
    // Repeat each one proportional to its rank:  (4, 4, 4, 4, 7, 7, 7, -8, -8, 1)
    hashes shouldBe Array((4, 4), (7, 3), (-8, 2), (1, 1))
  }

  test("ties") {
    // Since index 1 and 2 are tied, index 5 should have freq = 2 instead of 3.
    val mlsh = new PermutationLshModel(4, true)
    val vec = Vec.DenseFloat(2f, 2f, 0f, 0f, 1f, 4f)
    val hashes = mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq))
    hashes.sorted shouldBe Array((6, 4), (1, 3), (2, 3), (5, 1)).sorted
  }

  test("deterministic hashing") {
    implicit val rng: Random = new Random(0)
    val dims = 1024
    val mlsh = new PermutationLshModel(128, true)
    (0 until 100).foreach { _ =>
      val vec = Vec.DenseFloat.random(dims)
      val hashes = (0 until 100).map(_ => mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq)).mkString(","))
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
        hd.foreach(h => d.add(new Field("vec", h.hash, ft)))
        w.addDocument(d)
      }
    } {
      case (reader, searcher) =>
        val f: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
          (_: LeafReaderContext) => (_: Int, matches: Int) => matches * 1f

        val q = new MatchHashesAndScoreQuery("vec", hq, 2, 1f, reader, f)
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
          HashingQuery.index("vec", ft, v, lsh.hash(v.values)).foreach(d.add)
          w.addDocument(d)
        }
      } {
        case (r, s) =>
          queryVecs.map { v =>
            val q = HashingQuery("vec", v, 200, 1f, lsh.hash(v.values), ExactSimilarityFunction.Angular, r)
            s.search(q, 100).scoreDocs.map(sd => (sd.doc, sd.score)).toVector
          }
      }
      queryResults
    }
    val distinct = repeatedResults.distinct
    distinct.length shouldBe 1
  }

}
