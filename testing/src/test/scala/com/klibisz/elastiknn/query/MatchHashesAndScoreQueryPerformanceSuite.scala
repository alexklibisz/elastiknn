package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.{AngularLsh, ExactSimilarityFunction}
import com.klibisz.elastiknn.testing.LuceneSupport
import org.apache.lucene.document.Document
import org.scalatest._

import scala.util.Random

class MatchHashesAndScoreQueryPerformanceSuite extends FunSuite with Matchers with LuceneSupport {

  // Hacky way to give you some time for setting up the Profiler :)
//  import java.nio.file.{Files, Path}
//  private val p = Path.of("/tmp/wait")
//  if (!Files.exists(p)) Files.createFile(p)
//  while (Files.exists(p)) {
//    println(s"Waiting. Please delete ${p.toAbsolutePath.toString} to start.")
//    Thread.sleep(1000)
//  }

  test("indexing and searching on scale of GloVe-25") {
    implicit val rng: Random = new Random(0)
    val corpusVecs: Seq[Vec.DenseFloat] = Vec.DenseFloat.randoms(25, unit = true, n = 10000)
    val queryVecs: Seq[Vec.DenseFloat] = Vec.DenseFloat.randoms(25, unit = true, n = 100)
    val lshFunc = new AngularLsh(Mapping.AngularLsh(25, 50, 1))
    val exactFunc = ExactSimilarityFunction.Angular
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
        queryVecs.foreach { vec =>
          val q = HashingQuery(field, vec, 2000, lshFunc, exactFunc, r)
          val dd = s.search(q, 100)
          dd.scoreDocs should have length 100
        }
        info(s"Ran [${queryVecs.length}] searches in [${System.currentTimeMillis() - t0}] ms.")
    }
  }

}
