package com.klibisz.elastiknn

import java.io.FileOutputStream
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import com.klibisz.elastiknn.api.{Similarity, Vec, XContentCodec}
import io.circe._
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.vectors.PanamaFloatVectorOps
import io.circe.syntax._
import io.circe.generic.semiauto._

import scala.util.Random

case class Result(similarity: Similarity, values: Vector[Double])
object Result extends XContentCodecCirceBridge {
  implicit val codec: Codec[Result] = deriveCodec[Result]
}

case class Query(vector: Vec, results: Seq[Result])
object Query extends XContentCodecCirceBridge {
  implicit val codec: Codec[Query] = deriveCodec[Query]
}

case class TestData(corpus: Vector[Vec], queries: Vector[Query])
object TestData extends XContentCodecCirceBridge {

  implicit val codec: Codec[TestData] = deriveCodec[TestData]

  def read(fname: String): TestData = {
    val resource = getClass.getResource(fname)
    val gin = new GZIPInputStream(resource.openStream())
    val contents = new String(gin.readAllBytes())
    gin.close()
    io.circe.parser.decode[TestData](contents).toTry.get
  }

  def write(testData: TestData, fname: String): Unit = {
    val gout = new GZIPOutputStream(new FileOutputStream(fname))
    gout.write(testData.asJson.noSpaces.getBytes())
    gout.close()
  }

  def genSparseBool(dims: Int, numCorpus: Int, numQueries: Int, numNeighbors: Int)(implicit rng: Random): TestData = {
    // TODO: have a min and max bias to introduce more variety to the corpus.
    val corpus = Vec.SparseBool.randoms(dims, numCorpus, 0.2)
    val queries = Vec.SparseBool.randoms(dims, numQueries, 0.2).map { qv =>
      Query(
        qv,
        Seq(
          Result(Similarity.Jaccard, corpus.map(cv => ExactSimilarityFunction.Jaccard(cv, qv)).sorted.reverse.take(numNeighbors)),
          Result(Similarity.Hamming, corpus.map(cv => ExactSimilarityFunction.Hamming(cv, qv)).sorted.reverse.take(numNeighbors))
        )
      )
    }
    TestData(corpus, queries)
  }

  def genDenseFloat(dims: Int, numCorpus: Int, numQueries: Int, numNeighbors: Int, unit: Boolean = false)(implicit
      rng: Random
  ): TestData = {
    val l1 = new ExactSimilarityFunction.L1(new PanamaFloatVectorOps)
    val l2 = new ExactSimilarityFunction.L2(new PanamaFloatVectorOps)
    val cos = new ExactSimilarityFunction.Cosine(new PanamaFloatVectorOps)
    val corpus = Vec.DenseFloat.randoms(dims, numCorpus, unit)
    val queries = Vec.DenseFloat.randoms(dims, numQueries, unit).map { qv =>
      Query(
        qv,
        Seq(
          Result(Similarity.L1, corpus.map(cv => l1(cv, qv)).sorted.reverse.take(numNeighbors)),
          Result(Similarity.L2, corpus.map(cv => l2(cv, qv)).sorted.reverse.take(numNeighbors)),
          Result(Similarity.Cosine, corpus.map(cv => cos(cv, qv)).sorted.reverse.take(numNeighbors))
        )
      )
    }
    TestData(corpus, queries)
  }
}

/** Run this to generate test data. Then copy the json.gz files from the root directory to the resources directory.
  */
object Generate {

  import TestData._

  def main(args: Array[String]): Unit = {
    implicit val rng = new Random(0)
    val dims = 1024
    write(genSparseBool(dims, 5000, 50, 100), "testdata-sparsebool.json.gz")
    write(genDenseFloat(dims, 5000, 50, 100), "testdata-densefloat.json.gz")
    write(genDenseFloat(dims, 5000, 50, 100, unit = true), "testdata-densefloat-unit.json.gz")
  }

}

trait XContentCodecCirceBridge {
  implicit def encoderFromXContent[T: XContentCodec.Encoder]: Encoder[T] = new Encoder[T] {
    override def apply(a: T): Json = {
      val s = XContentCodec.encodeUnsafeToString(a)
      val p = io.circe.parser.parse(s)
      p.fold(throw _, identity)
    }
  }
  implicit def fromXContentCodecDecoder[T: XContentCodec.Decoder]: Decoder[T] = new Decoder[T] {
    override def apply(c: HCursor): Decoder.Result[T] = {
      val s = c.value.noSpacesSortKeys
      val t = XContentCodec.decodeUnsafeFromString[T](s)
      Right(t)
    }
  }
}
