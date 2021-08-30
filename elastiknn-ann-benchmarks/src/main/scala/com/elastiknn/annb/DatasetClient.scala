package com.elastiknn.annb

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.klibisz.elastiknn.api.Vec

import java.nio.file.Path

trait DatasetClient {
  def indexVectors(): Source[Vec, NotUsed]
  def queryVectors(): Source[Vec, NotUsed]
}

object DatasetClient {
  def apply(dataset: Dataset, path: Path): DatasetClient = dataset match {
    case d @ Dataset.FashionMnist => new AnnBenchmarksLocalHdf5Client(d, path)
  }
}

final class AnnBenchmarksLocalHdf5Client(dataset: Dataset with DatasetFormat.AnnBenchmarks, path: Path) extends DatasetClient {
  override def indexVectors(): Source[Vec, NotUsed] = ???
  override def queryVectors(): Source[Vec, NotUsed] = ???
}

final class BigAnnBenchmarksLocalCustomClient(dataset: Dataset with DatasetFormat.AnnBenchmarks, path: Path) extends DatasetClient {
  override def indexVectors(): Source[Vec, NotUsed] = ???
  override def queryVectors(): Source[Vec, NotUsed] = ???
}
