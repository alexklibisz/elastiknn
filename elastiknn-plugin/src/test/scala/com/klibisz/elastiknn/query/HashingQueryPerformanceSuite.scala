package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, L2LshModel}
import com.klibisz.elastiknn.lucene.{HashFieldType, LuceneSupport}
import com.klibisz.elastiknn.vectors.PanamaFloatVectorOps
import org.apache.lucene.codecs.lucene94.Lucene94Codec
import org.apache.lucene.document.Document
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class HashingQueryPerformanceSuite extends AnyFunSuite with Matchers with LuceneSupport {

//  // Hacky way to give you some time for setting up the Profiler :)
//  import java.nio.file.{Files, Path}
//  private val p = Path.of("/tmp/wait")
//  if (!Files.exists(p)) Files.createFile(p)
//  while (Files.exists(p)) {
//    println(s"Waiting. Please delete ${p.toAbsolutePath.toString} to start.")
//    Thread.sleep(1000)
//  }

  class BenchmarkCodec extends Lucene94Codec

  test("indexing and searching on scale of GloVe-25") {
    implicit val rng: Random = new Random(0)
    val corpusVecs: Seq[Vec.DenseFloat] = Vec.DenseFloat.randoms(128, n = 10000, unit = true)
    val queryVecs: Seq[Vec.DenseFloat] = Vec.DenseFloat.randoms(128, n = 1000, unit = true)
    val model = new L2LshModel(128, 100, 2, 1, new java.util.Random(0), new PanamaFloatVectorOps)
    val exactFunc = new ExactSimilarityFunction.L2(new PanamaFloatVectorOps)
    val field = "vec"
    val fieldType = HashFieldType.HASH_FIELD_TYPE
    indexAndSearch(codec = new BenchmarkCodec) { w =>
      val t0 = System.currentTimeMillis()
      for {
        v <- corpusVecs
        d = new Document
        fields = HashingQuery.index(field, fieldType, v, model.hash(v.values))
        _ = fields.foreach(d.add)
        _ = w.addDocument(d)
      } yield ()
      info(s"Indexed [${corpusVecs.length}] vectors in [${System.currentTimeMillis() - t0}] ms.")
    } { case (r, s) =>
      val t0 = System.currentTimeMillis()
      queryVecs.foreach { vec =>
        val q = new HashingQuery(field, vec, 100, model.hash(vec.values, 9), exactFunc)
        val dd = s.search(q.toLuceneQuery(r), 100)
        dd.scoreDocs should have length 100
      }
      info(s"Ran [${queryVecs.length}] searches in [${System.currentTimeMillis() - t0}] ms.")
    }
  }
}
