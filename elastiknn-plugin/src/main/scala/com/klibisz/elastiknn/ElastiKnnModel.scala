package com.klibisz.elastiknn

import scala.util._

trait ElastiKnnModel {
  def process(rawVector: String): Try[ProcessedVector]
  def search(rawVector: String)
}

final class ExactModel(dimension: Int, dist: Distance) extends ElastiKnnModel {
  override def process(rawVector: String): Try[ProcessedVector] = Try(rawVector.split(",").map(_.toFloat)) match {
    case Success(v) if v.length == dimension => Success(ExactVector(v))
    case Success(v)                          => Failure(new IllegalArgumentException(s"Expected dimension $dimension but got ${v.length}"))
    case Failure(t)                          => Failure(t)
  }
  override def search(rawVector: String): Unit = ???
}

//final class LshModel() extends ElastiKnnModel

object ElastiKnnModel {
//  def apply(popts: ProcessorOptions): Try[ElastiKnnModel] = popts.modelOptions match {
//    case Exact(_)   => Success(new ExactModel(popts.dimension, popts.distance))
//    case Lsh(mopts) => ???
//    case Empty      => Failure(new IllegalArgumentException(s"No model options given"))
//  }

  def apply(popts: ProcessorOptions): Try[ElastiKnnModel] = popts.modelOptions match {
    case _: ExactModelOptions => Success(new ExactModel(popts.dimension, popts.distance))
    case _                    => ???
  }

}
