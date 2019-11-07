package com.klibisz.elastiknn

import com.klibisz.elastiknn.ProcessedVector.ExactVector
import com.klibisz.elastiknn.ProcessedVector.Processed.Exact
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions

import scala.util._

trait Model {
  def process(rawVector: Array[Double]): Try[ProcessedVector]
  def search(rawVector: Array[Double])
}

final class ExactModel(dimension: Int) extends Model {
  override def process(rawVector: Array[Double]): Try[ProcessedVector] =
    if (rawVector.length == dimension) Success(ProcessedVector(Exact(ExactVector(rawVector))))
    else Failure(new IllegalArgumentException(s"Expected dimension $dimension but got ${rawVector.length}"))
  override def search(rawVector: Array[Double]): Unit = ???
}

final class LshModel() extends Model {
  override def process(rawVector: Array[Double]): Try[ProcessedVector] = Success(ProcessedVector(Exact(ExactVector())))
  override def search(rawVector: Array[Double]): Unit = ???
}

object Model {
  def apply(popts: ProcessorOptions): Try[Model] = popts.modelOptions match {
    case _: ModelOptions.Exact => Success(new ExactModel(popts.dimension))
    case _: ModelOptions.Lsh   => Success(new LshModel())
    case ModelOptions.Empty    => Failure(new IllegalArgumentException("Missing model options"))
  }
}
