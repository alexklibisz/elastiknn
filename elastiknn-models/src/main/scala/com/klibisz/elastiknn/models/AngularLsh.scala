//package com.klibisz.elastiknn.models
//
//import com.klibisz.elastiknn.api.{Mapping, Vec}
//import com.klibisz.elastiknn.storage.{BitBuffer, StoredVec}
//import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt
//
//import scala.util.Random
//
///**
//  * Locality sensitive hashing for Angular similarity using random hyperplanes as described in MMDS Chapter 3.
//  * @param mapping AngularLsh Mapping.
//  */
//final class AngularLsh(override val mapping: Mapping.AngularLsh)
//    extends HashingFunction[Mapping.AngularLsh, Vec.DenseFloat, StoredVec.DenseFloat] {
//
//  import mapping._
//
//  private implicit val rng: Random = new Random(0)
//
//  private val hashVecs: Array[Vec.DenseFloat] = (0 until (L * k)).map(_ => Vec.DenseFloat.random(dims)).toArray
//
//  override def apply(v: Vec.DenseFloat): Array[HashAndFreq] = {
//    val hashes = new Array[HashAndFreq](L)
//    var (ixHashes, ixHashVecs) = (0, 0)
//    while (ixHashes < L) {
//      val hashBuf = new BitBuffer.IntBuffer(writeInt(ixHashes))
//      var ixRows = 0
//      while (ixRows < k) {
//        if (hashVecs(ixHashVecs).dot(v) > 0) hashBuf.putOne() else hashBuf.putZero()
//        ixRows += 1
//        ixHashVecs += 1
//      }
//      hashes.update(ixHashes, HashAndFreq.once(hashBuf.toByteArray))
//      ixHashes += 1
//    }
//    hashes
//  }
//}
