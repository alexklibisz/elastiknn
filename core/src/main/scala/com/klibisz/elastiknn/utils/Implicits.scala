package com.klibisz.elastiknn.utils

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util

import com.google.common.collect.MinMaxPriorityQueue
import com.google.common.hash.{BloomFilter, Funnels}
import com.google.common.io.BaseEncoding
import com.google.protobuf.ByteString
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
import com.klibisz.elastiknn.storage.{StoredElastiKnnVector, StoredSparseBoolVector}
import com.klibisz.elastiknn.utils.CirceUtils.mapEncoder
import com.klibisz.elastiknn.{ElastiKnnVector, Similarity, SparseBoolVector}
import io.circe.syntax._
import scalapb.GeneratedMessageCompanion
import scalapb_circe.JsonFormat

import scala.util.{Random, Try}

trait Implicits extends ProtobufImplicits {

  implicit class SparseBoolVectorImplicits(sbv: SparseBoolVector) {

    lazy val length: Int = sbv.totalIndices

    lazy val nonEmpty: Boolean = length > 0

    lazy val isEmpty: Boolean = length == 0

    lazy val lengthTrue: Int = sbv.trueIndices.size

    lazy val lengthFalse: Int = sbv.totalIndices - sbv.trueIndices.size

    def compatibleWith(other: SparseBoolVector): Boolean = sbv.totalIndices == other.totalIndices

    def intersection(other: SparseBoolVector): SparseBoolVector =
      SparseBoolVector(sbv.trueIndices.intersect(other.trueIndices), sbv.totalIndices.min(other.totalIndices))

    def jaccardSim(other: SparseBoolVector): Double = {
      val isec: Int = sbv.intersection(other).lengthTrue
      val asum: Int = sbv.lengthTrue
      val bsum: Int = other.lengthTrue
      isec.toDouble / (asum + bsum - isec)
    }

    def jaccardDist(other: SparseBoolVector): Double = 1 - jaccardSim(other)

    def denseArray(): Array[Boolean] = (0 until sbv.totalIndices).toArray.map(sbv.trueIndices)

    def values(i: Int): Boolean = sbv.trueIndices.contains(i)

  }

  implicit class SparseBoolVectorCompanionImplicits(sbvc: GeneratedMessageCompanion[SparseBoolVector]) {
    def from(v: Iterable[Boolean]): SparseBoolVector = SparseBoolVector(
      trueIndices = v.zipWithIndex.filter(_._1).map(_._2).toSet,
      totalIndices = v.size
    )
    def random(totalIndices: Int, bias: Double = 0.5)(implicit rng: Random): SparseBoolVector =
      from((0 until totalIndices).map(_ => rng.nextDouble() <= bias))
    def randoms(totalIndices: Int, n: Int, bias: Double = 0.5)(implicit rng: Random): Vector[SparseBoolVector] =
      (0 until n).map(_ => random(totalIndices, bias)).toVector
  }

  implicit class StoredSparseBoolVectorImplicits(ssbv: StoredSparseBoolVector) {
    private val bf = {
      val bais = new ByteArrayInputStream(BaseEncoding.base64.decode(ssbv.guavaBloomFilterBase64))
      try BloomFilter.readFrom[Integer](bais, Funnels.integerFunnel())
      finally bais.close()
    }
    def contains(i: Int): Boolean = bf.mightContain(i) && util.Arrays.binarySearch(ssbv.sortedTrueIndices, i) >= 0
//    def contains(i: Int): Boolean = util.Arrays.binarySearch(ssbv.sortedTrueIndices, i) >= 0
  }

  implicit class StoredSparseBoolVectorCompanionImplicits(ssbvc: GeneratedMessageCompanion[StoredSparseBoolVector]) {
    def from(sbv: SparseBoolVector): StoredSparseBoolVector = {
      val bf = BloomFilter.create[Integer](Funnels.integerFunnel(), sbv.totalIndices.toLong, 0.05)
      sbv.trueIndices.foreach(bf.put(_))
      val bfb64 = {
        val baos = new ByteArrayOutputStream()
        bf.writeTo(baos)
        try BaseEncoding.base64.encode(baos.toByteArray)
        finally baos.close()
      }
      StoredSparseBoolVector(bfb64, sbv.trueIndices.toArray.sorted, sbv.totalIndices)
    }
  }

  implicit class StoredElastiKnnVectorCompanionImplicits(sekvc: GeneratedMessageCompanion[StoredElastiKnnVector]) {
    def from(ekv: ElastiKnnVector): StoredElastiKnnVector =
      StoredElastiKnnVector(ekv.vector match {
        case ElastiKnnVector.Vector.SparseBoolVector(sbv) => StoredElastiKnnVector.Vector.SparseBoolVector(StoredSparseBoolVector.from(sbv))
        case ElastiKnnVector.Vector.FloatVector(fv)       => StoredElastiKnnVector.Vector.FloatVector(fv)
        case ElastiKnnVector.Vector.Empty                 => StoredElastiKnnVector.Vector.Empty
      })
  }

  implicit class ElastiKnnVectorCompanionImplicits(ekvc: GeneratedMessageCompanion[ElastiKnnVector]) {
    def from(m: java.util.Map[String, AnyRef]): Try[ElastiKnnVector] = Try(JsonFormat.fromJson[ElastiKnnVector](m.asJson(mapEncoder)))
  }

  implicit class ModelOptionsImplicits(mopts: ModelOptions) {

    /** Return the processed field name. */
    private[elastiknn] lazy val fieldProc: Option[String] = mopts match {
      case ModelOptions.Exact(_) | ModelOptions.Empty => None
      case ModelOptions.Jaccard(j)                    => Some(j.fieldProcessed)
    }

    private[elastiknn] lazy val similarity: Option[Similarity] = mopts match {
      case ModelOptions.Exact(eopts) => Some(eopts.similarity)
      case ModelOptions.Jaccard(_)   => Some(SIMILARITY_JACCARD)
      case _                         => None
    }

  }

  implicit class TraversableImplicits[T](trv: Traversable[T]) {

    def topK(k: Int)(implicit ev: Ordering[T]): Traversable[T] = {
      val heap: MinMaxPriorityQueue[T] = MinMaxPriorityQueue.orderedBy(ev).create()
      for (v <- trv) {
        if (heap.size < k) heap.add(v)
        else if (ev.compare(v, heap.peekFirst()) > 0) {
          heap.removeFirst()
          heap.add(v)
        }
      }
      (0 until heap.size()).map(_ => heap.removeLast())
    }

    def topK[U](k: Int, by: T => U)(implicit ord: Ordering[U]): Traversable[T] = {
      object OrdT extends Ordering[T] {
        override def compare(x: T, y: T): Int = ord.compare(by(x), by(y))
      }
      this.topK(k)(OrdT)
    }

    def bottomK(k: Int)(implicit ev: Ordering[T]): Traversable[T] = topK(k)(ev.reverse)

    def bottomK[U](k: Int, by: T => U)(implicit ord: Ordering[U]): Traversable[T] = topK(k, by)(ord.reverse)

  }

}

object Implicits extends Implicits
