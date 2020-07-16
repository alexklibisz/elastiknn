package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * Locality sensitive hashing for L2 similarity based on MMDS Chapter 3.
  * Also drew some inspiration from this closed pull request: https://github.com/elastic/elasticsearch/pull/44374
  *
  * @param mapping L2Lsh Mapping. The members are used as follows:
  *                bands: number of bands, each containing `rows` hash functions. Generally, more bands yield higher recall.
  *                       Note that this often referred to as `L`, or the number of hash tables.
  *                rows: number of rows per band. Generally, more rows yield higher precision.
  *                      Note that this is often called `k`, or the number of functions per hash table.
  *                width: width of the interval that determines two floating-point hashed values are equivalent.
  *
  */
final class L2Lsh(override val mapping: Mapping.L2Lsh) extends HashingFunction[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat] {

  import mapping._
  private implicit val rng: Random = new Random(0)
  private val hashVecs: Array[Vec.DenseFloat] = (0 until (L * k)).map(_ => Vec.DenseFloat.random(dims)).toArray
  private val biases: Array[Float] = (0 until (L * k)).map(_ => rng.nextFloat() * r).toArray

  override def apply(v: Vec.DenseFloat): Array[Array[Byte]] = {
    val hashes = new Array[Array[Byte]](L)
    var ixHashes = 0
    var ixHashVecs = 0
    while (ixHashes < L) {
      val lBarr = writeInt(ixHashes)
      val hashBuf = new ArrayBuffer[Byte](lBarr.length + k * 4)
      hashBuf.appendAll(lBarr)
      var ixRows = 0
      while (ixRows < k) {
        val hash = math.floor((hashVecs(ixHashVecs).dot(v) + biases(ixHashVecs)) / r).toInt
        hashBuf.appendAll(writeInt(hash))
        ixRows += 1
        ixHashVecs += 1
      }
      hashes.update(ixHashes, hashBuf.toArray)
      ixHashes += 1
    }
    hashes
  }
}
