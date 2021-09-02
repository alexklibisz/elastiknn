package com.elastiknn.annb

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, Uri}
import akka.stream.scaladsl.{FileIO, Source}
import com.klibisz.elastiknn.api.Vec
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.hdf5.{DataType, H5F_ACC_RDONLY, H5File, PredType}

import java.nio.FloatBuffer
import java.nio.file.Path
import scala.concurrent.Future

trait DatasetClient[V <: Vec.KnownDims] {

  /**
    * Provide an akka-stream Source for vectors that should be indexed.
    * If the dataset is not cached in local storage already, this should also download it.
    */
  def indexVectors(): Source[V, NotUsed]

  /**
    * Provide an akka-stream Source for vecotrs that should be used for querying.
    * If the dataset is not cached in local storage already, this should also download it.
    */
  def queryVectors(): Source[V, NotUsed]
}

object DatasetClient {
  def apply(dataset: Dataset, path: Path): DatasetClient[dataset.V] =
    dataset match {
      case d @ Dataset.FashionMnist   => new AnnBenchmarksLocalHdf5Client(d, path)
      case d @ Dataset.Glove25Angular => new AnnBenchmarksLocalHdf5Client(d, path)
    }
}

/**
  * Client that reads datasets in the ann-benchmarks HDF5 format.
  *
  * Each dataset is in a single HDF5 file at http://ann-benchmarks.com/<dataset-name>.hdf5
  * e.g., http://ann-benchmarks.com/fashion-mnist-784-euclidean.hdf5
  */
final class AnnBenchmarksLocalHdf5Client[D <: Dataset](
    val dataset: D,
    path: Path
) extends DatasetClient[D#V] {

  private val localHdf5Path = path.resolve(s"${dataset.name}.hdf5")

  private def download() =
    Source
      .fromMaterializer {
        case (mat, _) =>
          implicit val exc = mat.executionContext
          implicit val sys = mat.system
          val log = mat.system.log
          if (localHdf5Path.toFile.exists()) Source.single(())
          else
            Source.lazyFuture { () =>
              val uri = Uri(s"http://ann-benchmarks.com/${dataset.name}.hdf5")
              val req = Get(uri)
              val resF = Http().singleRequest(req)
              resF.flatMap {
                case HttpResponse(StatusCodes.OK, _, entity, _) =>
                  log.info(s"Downloading dataset ${dataset.name} from $uri to $localHdf5Path")
                  entity.dataBytes
                    .runWith(FileIO.toPath(localHdf5Path))
                    .map(_ => log.info(s"Finished downloading dataset ${dataset.name} to $localHdf5Path"))
                case other => Future.failed(new Throwable(s"Non-200 response: $other"))
              }
            }
      }
      .mapMaterializedValue(_ => NotUsed)

  private def readVectors(name: String): Iterator[dataset.V] = {
    val f = new H5File(localHdf5Path.toFile.getAbsolutePath, H5F_ACC_RDONLY)
    val dataSet = f.openDataSet(name)
    val space = dataSet.getSpace
    val (rows, cols) = {
      val buf = Array(0L, 0L)
      space.getSimpleExtentDims(buf)
      (buf(0).toInt, buf(1).toInt)
    }
    try {
      // TODO: Read and emit the vectors in batches.
      val buf = FloatBuffer.allocate(rows * cols)
      val ptr = new FloatPointer(buf)
      val typ = new DataType(PredType.NATIVE_FLOAT())
      dataSet.read(ptr, typ)
      ptr.get(buf.array())
//      buf.array().grouped(cols).map(ev(_))
      ???
    } finally {
      dataSet.deallocate()
      space.deallocate()
      f.close()
    }
  }

  override def indexVectors(): Source[dataset.V, NotUsed] =
    download()
      .flatMapConcat(_ => Source.fromIterator(() => readVectors("train")))

  override def queryVectors(): Source[dataset.V, NotUsed] = ???
}

object AnnBenchmarksLocalHdf5Client {

  sealed trait ReadFromFloatArray[V <: Vec.KnownDims] {
    def apply(arr: Array[Float]): V
  }

  object ReadFromFloatArray {
    implicit def readDenseFloatFromFloatArray: ReadFromFloatArray[Vec.DenseFloat] = new ReadFromFloatArray[Vec.DenseFloat] {
      override def apply(arr: Array[Float]): Vec.DenseFloat = Vec.DenseFloat(arr)
    }
  }

}

//final class BigAnnBenchmarksLocalCustomClient(dataset: Dataset with DatasetFormat.AnnBenchmarks, path: Path) extends DatasetClient {
//  override def indexVectors(): Source[Vec, NotUsed] = ???
//  override def queryVectors(): Source[Vec, NotUsed] = ???
//}
