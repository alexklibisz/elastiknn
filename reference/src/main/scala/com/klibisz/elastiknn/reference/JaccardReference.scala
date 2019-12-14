package com.klibisz.elastiknn.reference

import com.klibisz.elastiknn.SparseBoolVector
import com.klibisz.elastiknn.utils.Implicits._
import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.feature.MinHashLSH
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.util.Random
import scala.util.hashing.MurmurHash3

trait JaccardModel {

  /** Find the nearest neighbors to the given query and return their indices and distances from the original corpus. */
  def query(corpus: Vector[SparseBoolVector], queries: Vector[SparseBoolVector], k: Int): Seq[Vector[Int]]

}

object ExactJaccardModel extends JaccardModel {

  def query(corpus: Vector[SparseBoolVector], queries: Vector[SparseBoolVector], k: Int): Seq[Vector[Int]] =
    for (query <- queries)
      yield
        corpus.zipWithIndex
          .map {
            case (v, i) => i -> v.jaccardSim(query)
          }
          .sortBy(_._2 * -1)
          .take(k)
          .map(_._1)
          .toVector

}

class MinhashJaccardModel(numTables: Int, numBands: Int, numRows: Int) extends JaccardModel {

  // Based loosely on: https://github.com/chrisjmccormick/MinHash/blob/master/runMinHashExample.py

  // Same as what's used in Spark.
  private val HASH_PRIME = 2038074743

  def query(corpus: Vector[SparseBoolVector], queries: Vector[SparseBoolVector], k: Int): Seq[Vector[Int]] = {

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

      candidateSims.sortBy(_._2 * -1).take(k).map(_._1).toVector
    }
  }
}

class MinhashJaccardModel2(numTables: Int) extends JaccardModel {

  // Same as what's used in Spark.
  private val HASH_PRIME = 2038074743

  /** Find the nearest neighbors to the given query and return their indices from the original corpus. */
  def query(corpus: Vector[SparseBoolVector], queries: Vector[SparseBoolVector], k: Int): Seq[Vector[Int]] = {

    val rng = new scala.util.Random(0)

    val randCoefficients: Seq[(Int, Int)] = for {
      _ <- 0 until numTables
    } yield (1 + rng.nextInt(HASH_PRIME - 1), rng.nextInt(HASH_PRIME - 1))

    def hashFunction(v: SparseBoolVector): Seq[Double] =
      randCoefficients.map {
        case (a, b) =>
          v.trueIndices
            .map { elem: Int =>
              ((1L + elem) * a + b) % HASH_PRIME
            }
            .min
            .toDouble
      }

    def sameBucket(x: Seq[Double], y: Seq[Double]): Boolean = x.zip(y).exists(tuple => tuple._1 == tuple._2)

    val transformedCorpus: Seq[Seq[Double]] = corpus.map(hashFunction)

    queries.map { q =>
      val transformedQuery: Seq[Double] = hashFunction(q)
      val candidateIndices: Seq[Int] = transformedCorpus.zipWithIndex
        .filter {
          case (t, i) => sameBucket(transformedQuery, t)
        }
        .map(_._2)
      candidateIndices.map(i => i -> corpus(i).jaccardSim(q)).sortBy(_._2 * -1).take(k).map(_._1).toVector
    }
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
  def query(corpus: Vector[SparseBoolVector], queries: Vector[SparseBoolVector], k: Int): Seq[Vector[Int]] = {

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
      ds.toDF.rdd
        .map(r => r.getInt(0))
        .collect()
        .toVector
    }
  }
}

object JaccardReference {

  def recall(relevant: Vector[Int], retrieved: Vector[Int]): Double =
    relevant.toSet.intersect(retrieved.toSet).size * 1.0 / retrieved.size

  def meanRecall(relevant: Seq[Vector[Int]], retrieved: Seq[Vector[Int]]): Double =
    relevant.zip(retrieved).map(x => recall(x._1, x._2)).sum * 1.0 / relevant.length

  def main(args: Array[String]): Unit = {

    Logger.getLogger("org.apache.spark").setLevel(Level.OFF)

    implicit val rng: Random = new scala.util.Random(0)
    implicit val ss: SparkSession = SparkSession.builder.master("local").appName("Jaccard Reference").getOrCreate()

    val corpusSize = 100
    val numQueries = 10
    val numTables = 10
    val dim = 128
    val k1 = 20

    // Random corpus and queries.
    val corpus: Vector[SparseBoolVector] = SparseBoolVector.randoms(dim, corpusSize)
    val queries: Vector[SparseBoolVector] = SparseBoolVector.randoms(dim, numQueries)

    val exactResult = ExactJaccardModel.query(corpus, queries, k1)
    val modelResult = new MinhashJaccardModel(numTables, 1, 1).query(corpus, queries, k1)
    val model2Result = new MinhashJaccardModel2(numTables).query(corpus, queries, k1)
    val sparkResult = new SparkModel(numTables).query(corpus, queries, k1)

    println(f"Model 1: ${meanRecall(exactResult, modelResult)}%.3f")
    println(f"Model 2: ${meanRecall(exactResult, model2Result)}%.3f")
    println(f"Spark  : ${meanRecall(exactResult, sparkResult)}%.3f")

  }

}
