package com.klibisz.elastiknn.reference

import org.apache.commons.math3.primes.Primes

import scala.util.Random
import scala.util.hashing.MurmurHash3

object Jaccard {

  def exact(a: Vector[Boolean], b: Vector[Boolean]): Double = {
    val isec = a.zip(b).count { case (ai, bi) => ai && bi }
    val asum = a.count(identity)
    val bsum = b.count(identity)
    isec * 1.0 / (asum + bsum - isec)
  }

  // Returns the mapping of index -> similarity.
  def exact(corpus: Vector[Vector[Boolean]], query: Vector[Boolean]): Vector[Double] =
    for (c <- corpus) yield exact(c, query)

  /**
    * Approximate Jaccard using Minhashing.
    * Based loosely on:
    *  - https://github.com/chrisjmccormick/MinHash/blob/master/runMinHashExample.py
    * @param corpus
    * @param query
    * @param numBands
    * @param numRowsInBand
    * @return
    */
  def approxMinhash(corpus: Vector[Vector[Boolean]], query: Vector[Boolean], numBands: Int, numRowsInBand: Int)(
      implicit rng: Random): Vector[Double] = {

    val dim = corpus.head.length
    val numHashes = numBands * numRowsInBand

    // Define hash functions.
    val nextPrime: Int = Primes.nextPrime(dim)
    val hashFuncs: Seq[Int => Int] = for {
      _ <- 0 until numHashes
      a = rng.nextInt(dim)
      b = rng.nextInt(dim)
    } yield (x: Int) => (a * x + b) % nextPrime

    // Compute corpus and query signatures.
    def signature(vec: Vector[Boolean]): Vector[Int] =
      for {
        f <- hashFuncs.toVector
        hashed = vec.zipWithIndex.filter(_._1).map(_._2).map(f)
      } yield if (hashed.nonEmpty) hashed.min else Int.MaxValue

    val corpusSignatures: Seq[Vector[Int]] = corpus.map(signature)
    require(corpusSignatures.length == corpus.length)
    corpusSignatures.foreach(sig => require(sig.length == numHashes, "One signature per hash function"))

    val querySignature: Vector[Int] = signature(query)

    // Group into bands.
    val corpusSignaturesBanded: Seq[Vector[Vector[Int]]] = corpusSignatures.map(_.grouped(numRowsInBand).toVector)
    val querySignatureBanded: Vector[Vector[Int]] = querySignature.grouped(numRowsInBand).toVector

    // Hash each band.
    val corpusSignaturesHashed: Seq[Vector[Int]] = corpusSignaturesBanded.map(_.map(MurmurHash3.orderedHash))
    val querySignatureHashed: Vector[Int] = querySignatureBanded.map(MurmurHash3.orderedHash)

    // Approximate jaccard similarity by computing the fraction of equivalent hashed bands.
    val approx: Seq[Double] = for {
      c <- corpusSignaturesHashed
      n = c.zip(querySignatureHashed).count { case (hc, hq) => hc == hq }
    } yield n * 1.0 / numBands

    approx.toVector
  }

  def main(args: Array[String]): Unit = {

    implicit val rng = new scala.util.Random(0)
    val corpusSize = 10

    val k = 50 // Dimensions of each vector

    // Random corpus.
    val corpus: Vector[Vector[Boolean]] = for {
      _ <- (0 until corpusSize).toVector
    } yield (0 until k).toVector.map(_ => rng.nextBoolean())

    // Random query vector.
    val query: Vector[Boolean] = (0 until k).toVector.map(_ => rng.nextBoolean())

    println(s"Q ${query.mkString(",")}")
    for (c <- corpus) println(s"C ${c.mkString(",")}")

    println("Exact jaccard:")
    println(exact(corpus, query).map(s => f"$s%.3f").mkString(","))

    println("Approximate minhash:")
    for (i <- 0 to 20) {
      val approx = approxMinhash(corpus, query, 10, 2)(new Random(i))
      println(approx.map(s => f"$s%.3f").mkString(","))
    }

  }

}
