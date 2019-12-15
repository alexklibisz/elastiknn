package com.klibisz.elastiknn.utils

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.SparseBoolVector
import scalapb.GeneratedMessageCompanion

import scala.util.Random

object Implicits {

  implicit class SparseBoolVectorImplicits(sbv: SparseBoolVector) {

    def length: Int = sbv.totalIndices

    def lengthTrue: Int = sbv.trueIndices.size

    def lengthFalse: Int = sbv.totalIndices - sbv.trueIndices.size

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
