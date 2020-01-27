package org.elasticsearch.elastiknn.utils

import com.google.common.collect.MinMaxPriorityQueue
import io.circe.syntax._
import org.elasticsearch.elastiknn.ProcessorOptions.ModelOptions
import org.elasticsearch.elastiknn.Similarity.SIMILARITY_JACCARD
import org.elasticsearch.elastiknn.utils.CirceUtils.mapEncoder
import org.elasticsearch.elastiknn.{ElastiKnnVector, Similarity, SparseBoolVector}
import scalapb.GeneratedMessageCompanion
import scalapb_circe.JsonFormat

import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.util.{Failure, Random, Success, Try}

trait Implicits extends ProtobufImplicits {

  implicit class SparseBoolVectorImplicits(sbv: SparseBoolVector) {

    lazy val length: Int = sbv.totalIndices

    lazy val nonEmpty: Boolean = length > 0

    lazy val isEmpty: Boolean = length == 0

    lazy val lengthTrue: Int = sbv.trueIndices.length

    lazy val lengthFalse: Int = sbv.totalIndices - sbv.trueIndices.length

    def compatibleWith(other: SparseBoolVector): Boolean = sbv.totalIndices == other.totalIndices

    def denseArray(): Array[Boolean] = {
      val arr = Array.fill(sbv.totalIndices)(false)
      sbv.trueIndices.foreach(i => arr.update(i, true))
      arr
    }

    def values(i: Int): Boolean = sbv.trueIndices.contains(i)

    /** Return a copy of the vector with its indices sorted. */
    def sorted(): SparseBoolVector = sbv.copy(trueIndices = sbv.trueIndices.sorted)

  }

  implicit class SparseBoolVectorCompanionImplicits(sbvc: GeneratedMessageCompanion[SparseBoolVector]) {
    def from(v: Iterable[Boolean]): SparseBoolVector = SparseBoolVector(
      trueIndices = v.zipWithIndex.filter(_._1).map(_._2).toArray,
      totalIndices = v.size
    )
    def random(totalIndices: Int, bias: Double = 0.5)(implicit rng: Random): SparseBoolVector =
      from((0 until totalIndices).map(_ => rng.nextDouble() <= bias))
    def randoms(totalIndices: Int, n: Int, bias: Double = 0.5)(implicit rng: Random): Vector[SparseBoolVector] =
      (0 until n).map(_ => random(totalIndices, bias)).toVector
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

    /** Compute number of equivalent elements in O(d) time assuming both traversables are sorted. Returns a Failure
      * if either of the Traversables is unsorted. */
    def sortedIntersectionCount(other: Traversable[T])(implicit ord: Ordering[T]): Try[Int] = {

      def unsorted(little: T, big: T): Failure[Int] =
        Failure(new IllegalArgumentException(s"Called on unsorted Traversable: $little came after $big"))

      @tailrec
      def impl(xs: Traversable[T], xmax: T, ys: Traversable[T], ymax: T, acc: Int): Try[Int] = (xs.headOption, ys.headOption) match {
        case (Some(xh), Some(yh)) =>
          lazy val xcmp = ord.compare(xh, xmax)
          lazy val ycmp = ord.compare(yh, ymax)
          lazy val xycmp = ord.compare(xh, yh)
          if (xcmp < 0) unsorted(xh, xmax)
          else if (ycmp < 0) unsorted(yh, ymax)
          else if (xycmp < 0) impl(xs.tail, xh, ys, ymax, acc)
          else if (xycmp > 0) impl(xs, xmax, ys.tail, yh, acc)
          else impl(xs.tail, xh, ys.tail, yh, acc + 1)
        case (Some(xh), None) => if (ord.compare(xh, xmax) < 0) unsorted(xh, xmax) else Success(acc)
        case (None, Some(yh)) => if (ord.compare(yh, ymax) < 0) unsorted(yh, ymax) else Success(acc)
        case _                => Success(acc)
      }

      (trv.headOption, other.headOption) match {
        case (Some(xh), Some(yh)) => impl(trv, xh, other, yh, 0)
        case _                    => Success(0)
      }
    }

  }

//  implicit class IndexedSeqImplicits[T: ClassTag](arr: IndexedSeq[T]) {
//
//    /** O(d) intersection assuming both Seq's are sorted in ascending order. */
//    def sortedIntersectionCount(other: IndexedSeq[T])(implicit ord: Ordering[T]): Int = {
//      val (a, b) = (arr, other)
//      var (ia, ib, n) = (0, 0, 0)
//      while (ia < a.length && ib < b.length) {
//        val cmp = ord.compare(a(ia), b(ib))
//        if (cmp < 0) ia += 1
//        else if (cmp > 0) ib += 1
//        else {
//          ia += 1
//          ib += 1
//          n += 1
//        }
//      }
//      n
//    }
//
//  }

}

object Implicits extends Implicits

object Test {
  def main(args: Array[String]): Unit = {
    val xs = Seq(1, 2, 2, 3)
    val ys = Seq(2, 2, 3, 4)
    println(xs)
    println(ys)
    println(Implicits.TraversableImplicits(xs).sortedIntersectionCount(ys))
  }
}
