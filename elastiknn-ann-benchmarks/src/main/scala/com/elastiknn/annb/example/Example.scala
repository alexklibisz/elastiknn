package com.elastiknn.annb.example

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Sink, Source}
import com.klibisz.elastiknn.api.Vec
import org.apache.lucene.index.IndexableField

import scala.concurrent.Future

object Example {

  sealed trait Dataset[V <: Vec.KnownDims] {
    def name: String
    def dims: Int
  }

  object Dataset {
    sealed trait DenseIntDataset extends Dataset[Vec.DenseInt]
    sealed trait DenseFloatDataset extends Dataset[Vec.DenseFloat]

    case object FashionMnist extends DenseIntDataset {
      override def name: String = "fashion-mnist"
      override def dims: Int = 768
    }

    case object Glove50 extends DenseFloatDataset {
      override def name: String = "glove-50"
      override def dims: Int = 50
    }
  }

  sealed trait Algorithm {
    def name: String
  }

  object Algorithm {
    case object ElastiknnL2Lsh extends Algorithm {
      def name: String = "elastiknn-l2lsh"
    }
  }

  sealed trait DatasetClient[V <: Vec.KnownDims] {
    def stream(): Source[V, NotUsed]
  }

  object DatasetClient {
    def apply[V <: Vec.KnownDims](dataset: Dataset[V]): DatasetClient[V] = dataset match {
      case Dataset.FashionMnist =>
        new DatasetClient[V] {
          override def stream(): Source[V, NotUsed] = Source.empty
        }
      case Dataset.Glove50 =>
        new DatasetClient[V] {
          override def stream(): Source[V, NotUsed] = Source.empty
        }
    }
  }

  sealed trait LuceneAlgorithm[V <: Vec.KnownDims] {
    def apply(id: Int, v: V): List[IndexableField]
  }

  object LuceneAlgorithm {

    class DenseIntAlgorithm() extends LuceneAlgorithm[Vec.DenseInt] {
      override def apply(id: Int, v: Vec.DenseInt): List[IndexableField] = {
        println(v.values.toList)
        List.empty
      }
    }

    class DenseFloatAlgorithm() extends LuceneAlgorithm[Vec.DenseFloat] {
      override def apply(id: Int, v: Vec.DenseFloat): List[IndexableField] = {
        println(v.values.toList)
        List.empty
      }
    }

    def apply[V <: Vec.KnownDims, D <: Dataset[V]](dataset: D, algo: Algorithm): LuceneAlgorithm[V] = (dataset, algo) match {
      case (_: Dataset.DenseIntDataset, Algorithm.ElastiknnL2Lsh)   => new DenseIntAlgorithm
      case (_: Dataset.DenseFloatDataset, Algorithm.ElastiknnL2Lsh) => new DenseFloatAlgorithm
      case _                                                        => ???
    }
  }

  object Sink {
    def store(): Sink[List[IndexableField], Future[Done]] = akka.stream.scaladsl.Sink.ignore
  }

  final case class Params[V_ <: Vec.KnownDims](dataset: Dataset[V_], algo: Algorithm) {
    type V = V_
  }

  def main(args: Array[String]): Unit = {
    val p1 = Params(Dataset.FashionMnist, Algorithm.ElastiknnL2Lsh)
    val dc1: DatasetClient[p1.V] = DatasetClient(p1.dataset)
    val la1: LuceneAlgorithm[p1.V] = LuceneAlgorithm(p1.dataset, Algorithm.ElastiknnL2Lsh)
    println((p1, dc1, la1))
    val p2 = Params(Dataset.Glove50, Algorithm.ElastiknnL2Lsh)
    val dc2: DatasetClient[p2.V] = DatasetClient(p2.dataset)
    val la2: LuceneAlgorithm[p2.V] = LuceneAlgorithm(p2.dataset, Algorithm.ElastiknnL2Lsh)
    println((p2, dc2, la2))

  }

}
