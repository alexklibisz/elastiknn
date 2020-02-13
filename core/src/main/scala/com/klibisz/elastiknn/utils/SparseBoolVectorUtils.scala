package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.{FloatVector, SparseBoolVector}
import scalapb.GeneratedMessageCompanion

import scala.util.Random

trait SparseBoolVectorUtils {

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

}

object SparseBoolVectorUtils extends SparseBoolVectorUtils
