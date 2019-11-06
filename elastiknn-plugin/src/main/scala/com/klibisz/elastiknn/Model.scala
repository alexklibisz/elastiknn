package com.klibisz.elastiknn

import com.klibisz.elastiknn.ProcessedVector.ExactVector
import com.klibisz.elastiknn.ProcessedVector.Processed.Exact
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions

import scala.util._

trait Model {
  def process(rawVector: String): Try[ProcessedVector]
  def search(rawVector: String)
}

final class ExactModel(dimension: Int) extends Model {
  override def process(rawVector: String): Try[ProcessedVector] = Try(rawVector.split(",").map(_.toFloat)) match {
    case Success(v) if v.length == dimension => Success(ProcessedVector(Exact(ExactVector(v))))
    case Success(v)                          => Failure(new IllegalArgumentException(s"Expected dimension $dimension but got ${v.length}"))
    case Failure(t)                          => Failure(t)
  }
  override def search(rawVector: String): Unit = ???
}

final class LshModel() extends Model {
  override def process(rawVector: String): Try[ProcessedVector] = Success(ProcessedVector(Exact(ExactVector())))
  override def search(rawVector: String): Unit = ???
}

object Model {
  def apply(popts: ProcessorOptions): Try[Model] = popts.modelOptions match {
    case _: ModelOptions.Exact => Success(new ExactModel(popts.dimension))
    case _: ModelOptions.Lsh   => Success(new LshModel())
    case ModelOptions.Empty    => Failure(new IllegalArgumentException("Missing model options"))
  }
}
