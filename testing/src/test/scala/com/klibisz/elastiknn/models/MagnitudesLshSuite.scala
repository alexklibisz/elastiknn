package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.mapper
import com.klibisz.elastiknn.query.HashingQuery
import com.klibisz.elastiknn.storage.UnsafeSerialization._
import com.klibisz.elastiknn.testing.LuceneSupport
import org.apache.lucene.document.{Document, Field}
import org.apache.lucene.index.{LeafReaderContext, Term}
import org.apache.lucene.search.MatchHashesAndScoreQuery
import org.apache.lucene.util.BytesRef
import org.scalatest._

class MagnitudesLshSuite extends FunSuite with Matchers with LuceneSupport {

  test("hashing example") {
    val mapping = Mapping.MagnitudesLsh(10, 4)
    val mlsh = new MagnitudesLsh(mapping)
    val vec = Vec.DenseFloat(10f, -2f, 0f, 99f, 0.1f, -8f, 42f, -13f, 6f, 0.1f)
    val hashes = mlsh(vec).map(readInt)
    // Get the top 4 indices by absolute value:   (3, 6, 7, 0)
    // Increment those with negative values:      (3, 6, 17, 0)
    // Repeat each one proportional to its rank:  (3, 3, 3, 3, 6, 6, 6, 17, 17, 0)
    hashes shouldBe Array(3, 3, 3, 3, 6, 6, 6, 17, 17, 0)
  }

  test("lucene example") {
    val mapping = Mapping.MagnitudesLsh(6, 3)
    val mlsh = new MagnitudesLsh(mapping)
    val ft = new mapper.VectorMapper.FieldType("elastiknn_dense_float_vector")

    val hd0 = Array(0, 0, 0, 2, 2, 4).map(writeInt)
    val hd1 = Array(4, 4, 4, 1, 1, 2).map(writeInt)
    val hq = Array(2, 2, 2, 4, 4, 0).map(writeInt)

    indexAndSearch(analyzer = ft.indexAnalyzer.analyzer()) { w =>
      Seq(hd0, hd1).foreach { hd =>
        val d = new Document()
        hd.foreach(h => d.add(new Field("vec", h, ft)))
        w.addDocument(d)
      }
    } {
      case (reader, searcher) =>
        val f: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
          (_: LeafReaderContext) =>
            (docId: Int, matches: Int) => {
              if (docId == 0) matches shouldBe (2 + 1 + 1)
              else if (docId == 1) matches shouldBe (1 + 2 + 0)
              1f
            }
        val q = new MatchHashesAndScoreQuery("vec", hq.map(new BytesRef(_)), 2, reader, f)
        searcher.search(q, 2).scoreDocs should have length 2
    }


  }

}
