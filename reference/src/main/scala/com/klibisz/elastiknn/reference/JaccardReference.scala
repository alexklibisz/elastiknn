package com.klibisz.elastiknn.reference

import com.klibisz.elastiknn.SparseBoolVector
import com.klibisz.elastiknn.utils.Implicits._
import org.apache.commons.math3.primes.Primes
import org.apache.spark.ml.feature.MinHashLSH

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

class MinhashJaccardModel(numTables: Int, numBands: Int, numRows: Int)(implicit rng: Random) extends JaccardModel {

  // Based loosely on: https://github.com/chrisjmccormick/MinHash/blob/master/runMinHashExample.py

  def query(corpus: Seq[SparseBoolVector], queries: Seq[SparseBoolVector], k: Int): Seq[Vector[Int]] = {

    val dim = corpus.head.length
    val numHashes = numBands * numRows
    val nextPrime = Primes.nextPrime(dim)

    // Each table is represented by a function mapping a vector to a hash value for each band.
    def table(): SparseBoolVector => Vector[Int] = {
      // Define hash functions.
      val hashFuncs: Vector[Int => Int] = (for {
        _ <- 0 until numHashes
        a = rng.nextInt(dim)
        b = rng.nextInt(dim)
      } yield (x: Int) => (a * x + b) % nextPrime).toVector

      // Compute signatures using hash functions.
      def sig(vec: SparseBoolVector): Vector[Int] =
        for {
          f <- hashFuncs
          hashed = vec.trueIndices.map(f)
        } yield if (hashed.nonEmpty) hashed.min else Int.MaxValue

      // Group into bands and hash.
      def hashBands(sig: Vector[Int]): Vector[Int] =
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
    for (q <- queries) yield {
      // Accumulate a list of candidates with possible duplicates.
      val candidates: IndexedSeq[Int] = for {
        (t, ti) <- tables.zipWithIndex
        (b, bi) <- t(q).zipWithIndex
        c <- index.getOrElse((ti, bi, b), Vector.empty[Int])
      } yield c

      // Flatten the list into a mapping from candidate id to its number of occurrences.
      val counted: Map[Int, Int] = candidates.groupBy(identity).mapValues(_.length)

      counted.toVector.topK(k, _._2).map(_._1).toVector
    }
  }
}

object JaccardReference {

  def spark(): Unit = {

    val mh = new MinHashLSH()
      .setNumHashTables(3)
      .setInputCol("keys")
      .setOutputCol("values")

  }

  def recall[T](relevant: Traversable[T], retrieved: Traversable[T]): Double =
    relevant.toSet.intersect(retrieved.toSet).size * 1.0 / retrieved.size

  def meanRecall[T](relevant: Seq[Traversable[T]], retrieved: Seq[Traversable[T]]): Double =
    relevant.zip(retrieved).map(x => recall(x._1.toSet, x._2.toSet)).sum * 1.0 / relevant.length

  def main(args: Array[String]): Unit = {

    implicit val rng = new scala.util.Random(0)

    val corpusSize = 100
    val numQueries = 10
    val dim = 128
    val k = 20

    // Random corpus and queries.
    val corpus: Seq[SparseBoolVector] = SparseBoolVector.random(dim, corpusSize)
    val queries: Seq[SparseBoolVector] = SparseBoolVector.random(dim, numQueries)

    val exactResult: Seq[Vector[Int]] = ExactJaccardModel.query(corpus, queries, k)
    val modelResult: Seq[Vector[Int]] = new MinhashJaccardModel(75, 20, 3).query(corpus, queries, k)

    print(meanRecall(exactResult, modelResult))

//    // Random query vector.
//    val query: Vector[Boolean] = (0 until k).toVector.map(_ => rng.nextBoolean())
//
//    println(s"Q ${query.mkString(",")}")
//    for (c <- corpus) println(s"C ${c.mkString(",")}")
//
//    println("Exact jaccard:")
//    val ex = new ExactJaccardModel(corpus)
//
//    println(exact(corpus, query).maxSortedIndices)
//
//    println("Spark minhash:")
//
//    println("Approximate minhash:")
//    for (i <- 0 to 20) {
//      val approx = approxMinhash(corpus, query, 10, 2)(new Random(i))
//      println(approx.maxSortedIndices)
//    }

  }

}
