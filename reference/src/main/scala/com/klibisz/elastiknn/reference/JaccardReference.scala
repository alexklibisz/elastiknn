package com.klibisz.elastiknn.reference

import com.klibisz.elastiknn.SparseBoolVector
import com.klibisz.elastiknn.utils.Implicits._
import org.apache.commons.math3.primes.Primes
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.ml.feature.MinHashLSH
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}

import scala.collection.immutable
import scala.util.Random
import scala.util.hashing.MurmurHash3

trait JaccardModel {

  /** Find the nearest neighbors to the given query and return their indices from the original corpus. */
  def query(corpus: Seq[SparseBoolVector], queries: Seq[SparseBoolVector], k: Int): Seq[Vector[Int]]

}

object ExactJaccardModel extends JaccardModel {

  def query(corpus: Seq[SparseBoolVector], queries: Seq[SparseBoolVector], k: Int): Seq[Vector[Int]] =
    for (query <- queries)
      yield
        corpus.zipWithIndex
          .map {
            case (v, i) => i -> v.jaccardSim(query)
          }
          .topK(k, _._2)
          .toVector
          .sortBy(_._2)
          .reverse
          .map(_._1)
          .toVector

}

class MinhashJaccardModel(numTables: Int, numBands: Int, numRows: Int) extends JaccardModel {

  // Based loosely on: https://github.com/chrisjmccormick/MinHash/blob/master/runMinHashExample.py

  // Same as what's used in Spark.
  private val HASH_PRIME = 2038074743

  def query(corpus: Seq[SparseBoolVector], queries: Seq[SparseBoolVector], k: Int): Seq[Vector[Int]] = {

    val rng = new scala.util.Random(0)
    val dim = corpus.head.length
    val numHashes = numBands * numRows

    // Each table is represented by a function mapping a vector to a hash value for each band.
    def table(): SparseBoolVector => Seq[Int] = {
      // Define hash function coefficients.
      val coefficients: Seq[(Int, Int)] = for {
        _ <- 0 until numHashes
        a = 1 + rng.nextInt(HASH_PRIME - 1)
        b = rng.nextInt(HASH_PRIME - 1)
      } yield (a, b)

      // Compute signatures using hash functions.
      def sig(vec: SparseBoolVector): Seq[Long] =
        for {
          (a, b) <- coefficients
          hashed = vec.trueIndices.map { elem =>
            ((1L + elem) * a + b) % HASH_PRIME
          }
        } yield if (hashed.nonEmpty) hashed.min else Long.MaxValue

      // Group into bands and hash.
      def hashBands(sig: Seq[Long]): Seq[Int] =
        for (group <- sig.grouped(numRows).toVector)
          yield MurmurHash3.orderedHash(group)

      (sbv: SparseBoolVector) =>
        hashBands(sig(sbv))
    }

    val tables = (0 until numTables).map(_ => table())

    // Index maps from (table number, band number, hash) to list of corpus indices.
    val preIndex = for {
      (t, ti) <- tables.zipWithIndex
      (c, ci) <- corpus.zipWithIndex
      (b, bi) <- t(c).zipWithIndex
    } yield (ti, bi, b) -> ci

    val index: Map[(Int, Int, Int), Vector[Int]] = preIndex.groupBy(_._1).mapValues(_.map(_._2).toVector)

    // Compute the hashes for each query vector, accumulate the list of candidates, count them to get the top k.
    queries.map { q =>
      // Accumulate a list of candidates with possible duplicates.
      val candidateIndices: IndexedSeq[Int] = for {
        (t, ti) <- tables.zipWithIndex
        (b, bi) <- t(q).zipWithIndex
        c <- index.getOrElse((ti, bi, b), Vector.empty[Int])
      } yield c

      // Compute the actual distance to each candidate.
      val candidateSims = candidateIndices.distinct.map(i => i -> corpus(i).jaccardSim(q))

      candidateSims.topK(k, _._2).map(_._1).toVector
    }
  }
}

class MinhashJaccardModel2(numTables: Int) extends JaccardModel {

  // Same as what's used in Spark.
  private val HASH_PRIME = 2038074743

  /** Find the nearest neighbors to the given query and return their indices from the original corpus. */
  def query(corpus: Seq[SparseBoolVector], queries: Seq[SparseBoolVector], k: Int): Seq[Vector[Int]] = {

    val rng = new scala.util.Random(0)

    val randCoefficients: Seq[(Int, Int)] = for {
      _ <- 0 until numTables
    } yield (1 + rng.nextInt(HASH_PRIME - 1), rng.nextInt(HASH_PRIME - 1))

    def hashFunction(v: SparseBoolVector): Seq[Int] =
      for {
        (a, b) <- randCoefficients
        hh = v.trueIndices.map { i =>
          ((1 + i) * a + b) % HASH_PRIME
        }
      } yield hh.min

    ???
  }
}

class SparkModel(numTables: Int)(implicit spark: SparkSession) extends JaccardModel {

  private def toSparkSparseVector(sbv: SparseBoolVector) = Vectors.sparse(sbv.totalIndices, sbv.trueIndices.map(_ -> 1.0).toSeq)

  private def toDF(vectors: Seq[SparseBoolVector]): DataFrame =
    spark
      .createDataFrame(vectors.zipWithIndex.map {
        case (v, i) => i -> toSparkSparseVector(v)
      })
      .toDF("id", "keys")

  /** Find the nearest neighbors to the given query and return their indices from the original corpus. */
  def query(corpus: Seq[SparseBoolVector], queries: Seq[SparseBoolVector], k: Int): Seq[Vector[Int]] = {

    val corpusDF = toDF(corpus)
    val model = new MinHashLSH()
      .setSeed(0)
      .setNumHashTables(numTables)
      .setInputCol("keys")
      .setOutputCol("values")
      .fit(corpusDF)
    val corpusTransformed = model.transform(corpusDF).cache()

    println("Before transforming:")
    corpusDF.show(10)
    println("After transforming:")
    corpusTransformed.show(10)

    for (q <- queries) yield {
      val ds = model.approxNearestNeighbors(corpusTransformed, toSparkSparseVector(q), k)
      ds.toDF.select("id").rdd.map(_.getInt(0)).collect().toVector
    }
  }
}

object JaccardReference {

  def recall[T](relevant: Traversable[T], retrieved: Traversable[T]): Double =
    relevant.toSet.intersect(retrieved.toSet).size * 1.0 / retrieved.size

  def meanRecall[T](relevant: Seq[Traversable[T]], retrieved: Seq[Traversable[T]]): Double =
    relevant.zip(retrieved).map(x => recall(x._1.toSet, x._2.toSet)).sum * 1.0 / relevant.length

  def main(args: Array[String]): Unit = {

    implicit val rng: Random = new scala.util.Random(0)
    implicit val ss: SparkSession = SparkSession.builder.master("local").appName("Jaccard Reference").getOrCreate()

    val corpusSize = 100
    val numQueries = 10
    val dim = 128
    val k = 20

    // Random corpus and queries.
    val corpus: Seq[SparseBoolVector] = SparseBoolVector.random(dim, corpusSize)
    val queries: Seq[SparseBoolVector] = SparseBoolVector.random(dim, numQueries)

    val exactResult: Seq[Vector[Int]] = ExactJaccardModel.query(corpus, queries, k)
    val sparkResult = new SparkModel(10).query(corpus, queries, k)
    val modelResult: Seq[Vector[Int]] = new MinhashJaccardModel(1, 10, 1).query(corpus, queries, k)

    println(meanRecall(exactResult, modelResult))
    println(meanRecall(exactResult, sparkResult))

  }

}
