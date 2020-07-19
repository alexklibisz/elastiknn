package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.AngularLsh
import com.klibisz.elastiknn.testing.LuceneHarness
import org.apache.lucene.document.Document
import org.scalatest._

import scala.util.Random

class HashingQueryPerformanceSuite extends FunSuite with Matchers with LuceneHarness {

  test("indexing and searching on par with GloVe-25") {
    implicit val rng: Random = new Random(0)
    val corpusVecs: Seq[Vec.DenseFloat] = Vec.DenseFloat.randoms(25, unit = true, n = 20000)
    val queryVecs: Seq[Vec.DenseFloat] = Vec.DenseFloat.randoms(25, unit = true, n = 1000)
    val lshFunc = new AngularLsh(Mapping.AngularLsh(25, 50, 1))
    val field = "vec"
    val fieldType = new VectorMapper.FieldType(field)
    indexAndSearch(analyzer = fieldType.indexAnalyzer().analyzer()) { w =>
      val t0 = System.currentTimeMillis()
      for {
        v <- corpusVecs
        d = new Document
        _ = HashingQuery.index(field, fieldType, v, lshFunc).foreach(d.add)
        _ = w.addDocument(d)
      } yield ()
      info(s"Indexed [${corpusVecs.length}] vectors in [${System.currentTimeMillis() - t0}] ms.")
    } {
      case (r, s) =>
        val t0 = System.currentTimeMillis()
        val times = queryVecs.foldLeft(Vector.empty[Long]) {
          case (accTimes, vec) =>
            val q = HashingQuery("vec", vec, 2000, lshFunc, r)
            val t0 = System.currentTimeMillis()
            val dd = s.search(q, 100)
            dd.scoreDocs should have length 100
            accTimes :+ System.currentTimeMillis() - t0
        }
        info(s"Ran [${times.length}] searches in [${System.currentTimeMillis() - t0}] ms.")
    }
  }

}
