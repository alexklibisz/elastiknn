package com.klibisz.elastiknn

import com.klibisz.elastiknn.ModelOptions.Empty

import scala.util._

trait Model {
  def process(rawVector: String): Try[ProcessedVector]
  def search(rawVector: String)
}

final class ExactModel(dimension: Int, dist: Distance) extends Model {
  override def process(rawVector: String): Try[ProcessedVector] = Try(rawVector.split(",").map(_.toFloat)) match {
    case Success(v) if v.length == dimension => Success(ExactVector(v))
    case Success(v)                          => Failure(new IllegalArgumentException(s"Expected dimension $dimension but got ${v.length}"))
    case Failure(t)                          => Failure(t)
  }
  override def search(rawVector: String): Unit = ???
}

final class LshModel() extends Model {
  override def process(rawVector: String): Try[ProcessedVector] = Success(ExactVector())
  override def search(rawVector: String): Unit = ???
}

object Model {
  def apply(popts: ProcessorOptions): Try[Model] = popts.modelOptions match {
    case _: ExactModelOptions => Success(new ExactModel(popts.dimension, popts.distance))
    case _: LshModelOptions   => Success(new LshModel())
    case Empty                => Failure(new IllegalArgumentException("Missing model options"))
  }
}
