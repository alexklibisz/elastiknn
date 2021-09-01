package com.elastiknn.annb

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, Uri}
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

  private val localHdf5Path = path.resolve(s"${dataset.name}.hdf5")

  private def download(): Source[Unit, NotUsed] =
    Source
      .fromMaterializer {
        case (mat, _) =>
          val log = mat.system.log
          implicit val ec: ExecutionContext = mat.executionContext
          if (localHdf5Path.toFile.exists()) Source.single(())
          else
            Source.lazyFuture { () =>
              val uri = Uri("http://ann-benchmarks.com/fashion-mnist-784-euclidean.hdf5")
              val req = Get(uri)
              val resF = Http()(mat.system).singleRequest(req)
              resF.flatMap {
                case okres @ HttpResponse(StatusCodes.OK, _, entity, _) =>
                  log.info(s"Downloading dataset ${dataset.name} from $uri to $localHdf5Path")
                  entity.dataBytes
                    .runWith(FileIO.toPath(localHdf5Path))(mat)
                    .map(_ => log.info(s"Finished downloading dataset ${dataset.name} to $localHdf5Path"))
                case other => Future.failed(new Throwable(s"Non-200 status code for ${other}"))
              }
            }
      }
      .mapMaterializedValue(_ => NotUsed)

  override def indexVectors(): Source[Vec, NotUsed] = {
    download()
      .flatMapConcat(_ => Source.single(Vec.Empty()))
  }

  override def queryVectors(): Source[Vec, NotUsed] = ???
}

final class BigAnnBenchmarksLocalCustomClient(dataset: Dataset with DatasetFormat.AnnBenchmarks, path: Path) extends DatasetClient {
  override def indexVectors(): Source[Vec, NotUsed] = ???
  override def queryVectors(): Source[Vec, NotUsed] = ???
}
