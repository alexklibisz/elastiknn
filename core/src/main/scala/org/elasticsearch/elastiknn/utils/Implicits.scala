package org.elasticsearch.elastiknn.utils

import com.google.common.collect.MinMaxPriorityQueue
import io.circe.syntax._
import org.elasticsearch.elastiknn.ProcessorOptions.ModelOptions
import org.elasticsearch.elastiknn.Similarity.SIMILARITY_JACCARD
import org.elasticsearch.elastiknn.utils.CirceUtils.javaMapEncoder
import org.elasticsearch.elastiknn.{ElastiKnnVector, FloatVector, Similarity, SparseBoolVector}
import scalapb.GeneratedMessageCompanion
import scalapb_circe.JsonFormat

import scala.util.{Random, Try}

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

  implicit class FloatVectorCompanionImplicits(fvc: GeneratedMessageCompanion[FloatVector]) {
    def random(length: Int, scale: Double = 1.0)(implicit rng: Random): FloatVector =
      FloatVector((0 until length).map(_ => rng.nextDouble() * scale).toArray)
    def randoms(length: Int, n: Int, scale: Double = 1.0)(implicit rng: Random): Vector[FloatVector] =
      (0 until n).map(_ => random(length, scale)).toVector
  }

  implicit class ElastiKnnVectorCompanionImplicits(ekvc: GeneratedMessageCompanion[ElastiKnnVector]) {
    def apply(fv: FloatVector): ElastiKnnVector = ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(fv))
    def apply(sbv: SparseBoolVector): ElastiKnnVector = ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv))
    def from(m: java.util.Map[String, AnyRef]): Try[ElastiKnnVector] = Try(JsonFormat.fromJson[ElastiKnnVector](m.asJson(javaMapEncoder)))
    def equal(ekv1: ElastiKnnVector, ekv2: ElastiKnnVector): Boolean = (ekv1, ekv2) match {
      case (ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(fv1)), ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(fv2))) =>
        fv1.values.sameElements(fv2.values)
      case (ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv1)),
            ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv2))) =>
        sbv1.trueIndices.sameElements(sbv2.trueIndices) && sbv1.totalIndices == sbv2.totalIndices
      case _ => false
    }

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

object Implicits extends Implicits with TryImplicits with ProtobufImplicits
