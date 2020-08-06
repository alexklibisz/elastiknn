package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, L2Lsh}
import com.klibisz.elastiknn.testing.LuceneSupport
import org.apache.lucene.codecs.lucene84.Lucene84Codec
import org.apache.lucene.codecs.memory._
import org.apache.lucene.codecs.{DocValuesFormat, PostingsFormat}
import org.apache.lucene.document.Document
import org.scalatest._

import scala.util.Random

class MatchHashesAndScoreQueryPerformanceSuite extends FunSuite with Matchers with LuceneSupport {

//  // Hacky way to give you some time for setting up the Profiler :)
//  import java.nio.file.{Files, Path}
//  private val p = Path.of("/tmp/wait")
//  if (!Files.exists(p)) Files.createFile(p)
//  while (Files.exists(p)) {
//    println(s"Waiting. Please delete ${p.toAbsolutePath.toString} to start.")
//    Thread.sleep(1000)
//  }

  class MemCodec extends Lucene84Codec {
    override def getDocValuesFormatForField(field: String): DocValuesFormat = new DirectDocValuesFormat
    override def getPostingsFormatForField(field: String): PostingsFormat = new DirectPostingsFormat()
  }

  test("indexing and searching on scale of GloVe-25") {
    implicit val rng: Random = new Random(0)
    val corpusVecs: Seq[Vec.DenseFloat] = Vec.DenseFloat.randoms(128, n = 10000, unit = true)
    val queryVecs: Seq[Vec.DenseFloat] = Vec.DenseFloat.randoms(128, n = 1000, unit = true)
    val lshFunc = new L2Lsh(Mapping.L2Lsh(128, 100, 2, 1))
    val exactFunc = ExactSimilarityFunction.L2
    val field = "vec"
    val fieldType = new VectorMapper.FieldType(field)
    indexAndSearch(codec = new MemCodec) { w =>
      val t0 = System.currentTimeMillis()
      for {
        v <- corpusVecs
        d = new Document
        fields = HashingQuery.index(field, fieldType, v, lshFunc)
        _ = fields.foreach(d.add)
        _ = w.addDocument(d)
      } yield ()
      info(s"Indexed [${corpusVecs.length}] vectors in [${System.currentTimeMillis() - t0}] ms.")
    } {
      case (r, s) =>
        val t0 = System.currentTimeMillis()
        queryVecs.foreach { vec =>
          val q = HashingQuery(field, vec, 100, lshFunc.hashWithProbes(vec, 9), exactFunc, r)
          val dd = s.search(q, 100)
          dd.scoreDocs should have length 100
        }
        info(s"Ran [${queryVecs.length}] searches in [${System.currentTimeMillis() - t0}] ms.")
    }
  }

}
