package com.elastiknn.annb

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.{FileIO, Source}
import com.klibisz.elastiknn.api.Vec

import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}

trait DatasetClient {

  /**
    * Provide an akka-stream Source for vectors that should be indexed.
    * If the dataset is not cached in local storage already, this should also download it.
    */
  def indexVectors(): Source[Vec, NotUsed]

  /**
    * Provide an akka-stream Source for vecotrs that should be used for querying.
    * If the dataset is not cached in local storage already, this should also download it.
    */
  def queryVectors(): Source[Vec, NotUsed]
}

object DatasetClient {
  def apply(dataset: Dataset, path: Path): DatasetClient = dataset match {
    case d @ Dataset.FashionMnist => new AnnBenchmarksLocalHdf5Client(d, path)
  }
}

/**
  * Client that reads datasets in the ann-benchmarks HDF5 format.
  *
  * Each dataset is in a single HDF5 file at http://ann-benchmarks.com/<dataset-name>.hdf5
  * e.g., http://ann-benchmarks.com/fashion-mnist-784-euclidean.hdf5
  *
  * TODO: Document the format.
  */
final class AnnBenchmarksLocalHdf5Client(dataset: Dataset with DatasetFormat.AnnBenchmarks, path: Path) extends DatasetClient {

  def download()(implicit sys: ActorSystem, ec: ExecutionContext): Future[Path] = {
    val out = path.resolve(s"${dataset.name}.hdf5")
    if (out.toFile.exists())
      Future.successful {
        sys.log.info(s"File $out already exists")
        out
      }
    else {
      val uri = Uri("http://ann-benchmarks.com/fashion-mnist-784-euclidean.hdf5")
      val req = Get(uri)
      val resF = Http().singleRequest(req)
      sys.log.info(s"Requesting $uri")
      for {
        res <- resF
        _ = sys.log.info(s"Found $res")
        _ = sys.log.info(s"Downloading $uri to $out")
        _ <- res.entity.dataBytes.runWith(FileIO.toPath(out))
      } yield out
    }
  }

  override def indexVectors(): Source[Vec, NotUsed] = {
    ???
  }
  override def queryVectors(): Source[Vec, NotUsed] = ???
}

final class BigAnnBenchmarksLocalCustomClient(dataset: Dataset with DatasetFormat.AnnBenchmarks, path: Path) extends DatasetClient {
  override def indexVectors(): Source[Vec, NotUsed] = ???
  override def queryVectors(): Source[Vec, NotUsed] = ???
}
