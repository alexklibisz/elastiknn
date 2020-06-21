package com.klibisz.elastiknn.testing

import java.io.FileOutputStream
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import com.klibisz.elastiknn.api.{Similarity, Vec}
import io.circe._
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import io.circe.syntax._
import io.circe.generic.semiauto._

import scala.util.{Random, Try}

case class Query(vector: Vec, similarities: Vector[Float], indices: Vector[Int])
object Query {
  implicit val codec: Codec[Query] = deriveCodec[Query]
}

case class TestData(corpus: Vector[Vec], queries: Vector[Query])
object TestData {

  implicit val codec: Codec[TestData] = deriveCodec[TestData]

  val generatedDims = Seq(10, 128, 1024)

  def read(similarity: Similarity, dims: Int): Try[TestData] = {
    val name = s"testdata-${similarity.toString.toLowerCase}-$dims.json.gz"
    val resource = getClass.getResource(name)
    val gin = new GZIPInputStream(resource.openStream())
    val contents = new String(gin.readAllBytes())
    io.circe.parser.decode[TestData](contents).toTry
  }

  def write(testData: TestData, similarity: Similarity, dims: Int): Unit = {
    val name = s"testdata-${similarity.toString.toLowerCase}-$dims.json.gz"
    val gout = new GZIPOutputStream(new FileOutputStream(name))
    gout.write(testData.asJson.noSpaces.getBytes())
    gout.close()
  }

  def genSparseBool(dims: Int, numCorpus: Int, numQueries: Int, numNeighbors: Int, simFunc: (Vec.SparseBool, Vec.SparseBool) => Double)(
      implicit rng: Random): TestData = {
    val corpus = Vec.SparseBool.randoms(dims, numCorpus)
    val queries = Vec.SparseBool.randoms(dims, numQueries).map { qv =>
      val nearest = corpus.map(cv => simFunc(cv, qv)).zipWithIndex.sortBy(_._1 * -1).take(numNeighbors)
      Query(qv, nearest.map(_._1.toFloat), nearest.map(_._2))
    }
    TestData(corpus, queries)
  }

  def genDenseFloat(dims: Int, numCorpus: Int, numQueries: Int, numNeighbors: Int, simFunc: (Vec.DenseFloat, Vec.DenseFloat) => Double)(
      implicit rng: Random): TestData = {
    val corpus = Vec.DenseFloat.randoms(dims, numCorpus)
    val queries = Vec.DenseFloat.randoms(dims, numQueries).map { qv =>
      val nearest = corpus.map(cv => simFunc(cv, qv)).zipWithIndex.sortBy(_._1 * -1).take(numNeighbors)
      Query(qv, nearest.map(_._1.toFloat), nearest.map(_._2))
    }
    TestData(corpus, queries)
  }
}

/**
  * Run this to generate test data.
  */
object Generate {

  import TestData._

  def main(args: Array[String]): Unit = {
    implicit val rng: Random = new Random(0)
    val numCorpus = 10000
    val numQueries = 100
    val numNeighbors = 100
    for {
      dims <- TestData.generatedDims
    } {
      write(genSparseBool(dims, numCorpus, numQueries, numNeighbors, ExactSimilarityReference.Jaccard), Similarity.Jaccard, dims)
      write(genSparseBool(dims, numCorpus, numQueries, numNeighbors, ExactSimilarityReference.Hamming), Similarity.Hamming, dims)
      write(genDenseFloat(dims, numCorpus, numQueries, numNeighbors, ExactSimilarityReference.Angular), Similarity.Angular, dims)
      write(genDenseFloat(dims, numCorpus, numQueries, numNeighbors, ExactSimilarityReference.L2), Similarity.L2, dims)
      write(genDenseFloat(dims, numCorpus, numQueries, numNeighbors, ExactSimilarityReference.L1), Similarity.L1, dims)
    }
  }

}
