package com.elastiknn.annb.example

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.elastiknn.annb.example.Example2.DatasetClient.Aux
import com.klibisz.elastiknn.api.Vec

object Example2 {

  sealed trait Dataset {
    type V <: Vec.KnownDims
    def name: String
    def dims: Int
  }

  object Dataset {
    case object FashionMnist extends Dataset {
      type V = Vec.DenseFloat
      override def name: String = "fashion-mnist"
      override def dims: Int = 768
    }
  }

  sealed trait DatasetClient {
    type V <: Vec.KnownDims
    def stream(): Source[V, NotUsed]
  }

  object DatasetClient {

    type Aux[VV] = DatasetClient { type V = VV }

    def apply[D <: Dataset](dataset: D)(implicit ev: Instance.Aux[D]): Aux[D#V] = dataset match {
      case Dataset.FashionMnist => ev.instance
    }

    private sealed trait Instance {
      type D <: Dataset
      def instance: DatasetClient.Aux[D#V]
    }

    private object Instance {
      type Aux[DD <: Dataset] = Instance { type D = DD }
      implicit val fashionMnist: Instance.Aux[Dataset.FashionMnist.type] = new Instance {
        override type D = Dataset.FashionMnist.type
        override def instance: DatasetClient.Aux[Vec.DenseFloat] = ???
      }
    }

  }

  final case class Params(dataset: Dataset)

  // TODO: Should probably just do a pattern match on the ~15 datasets,
  //  explicitly specify clients of specific types and pass them to a method
  //  that glues it all together.

  def main(args: Array[String]): Unit = {
    val p1 = Params(Dataset.FashionMnist)
    val dc1: Aux[Vec.DenseFloat] = DatasetClient(Dataset.FashionMnist)
    dc1.stream().map(_.values)
    println((p1, dc1))
  }

}
