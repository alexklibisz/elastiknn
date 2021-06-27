package com.klibisz.ann1b

import akka.stream.IOResult
import akka.stream.scaladsl._
import akka.util.ByteString

import java.nio.ByteOrder
import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

/**
  * Stream the datasets from the local filesystem.
  * Expects the dataset to be stored in the following format:
  *   - base data at ${directory}/base.${dataset.dtype.extension}
  *   - sample data at ${directory}/sample.${dataset.dtype.extension}
  *   - queries at ${directory}/query.${dataset.dtype.extension}
  *   - ground truth at ${directory}/gt.${dataset.dtype.extension}
  */
final class LocalDatasetSource(dataset: Dataset, directory: Path) {

  private val chunkSize: Int = dataset.dims * dataset.dtype.sizeOf

  private val byteStringToFloatArray = dataset.dtype match {
    case Datatype.U8Bin =>
      (bs: ByteString) => {
        val arr = new Array[Byte](dataset.dims)
        bs.asByteBuffer.get(arr, 0, arr.length)
        arr.map(b => (b.toInt & 0xFF).toFloat)
      }

    case Datatype.I8Bin =>
      (bs: ByteString) => {
        val arr = new Array[Byte](dataset.dims)
        bs.asByteBuffer.get(arr, 0, arr.length)
        arr.map(_.toFloat)
      }

    case Datatype.FBin =>
      (bs: ByteString) => {
        val arr = new Array[Float](dataset.dims)
        val buf = bs.asByteBuffer.alignedSlice(4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        buf.get(arr, 0, arr.length)
        arr
      }
  }

  def baseData(parallelism: Int): Source[Dataset.Doc, Future[IOResult]] = {
    ???
  }

  def sampleData(parallelism: Int)(implicit ec: ExecutionContext): Source[Dataset.Doc, Future[IOResult]] = {
    val path = Paths.get(directory.toFile.getAbsolutePath, s"sample.${dataset.dtype.extension}")
    FileIO
      .fromPath(path, chunkSize = chunkSize, startPosition = 8)
      .zipWithIndex
      .mapAsyncUnordered(parallelism) {
        case (bs, ix) => Future(Dataset.Doc(ix, byteStringToFloatArray(bs)))
      }
  }

  def queryData(parallelism: Int): Source[Dataset.Doc, Future[IOResult]] = ???

}

object LocalDatasetSource {

  /**
    * Create a LocalDatasetSource that expects data in the directory:
    * $HOME/.big-ann-benchmarks/data/${dataset.name}
    * @param dataset
    * @return
    */
  def apply(dataset: Dataset): LocalDatasetSource =
    new LocalDatasetSource(
      dataset,
      Paths.get(System.getProperty("user.home"), ".big-ann-benchmarks", "data", dataset.name)
    )
}
