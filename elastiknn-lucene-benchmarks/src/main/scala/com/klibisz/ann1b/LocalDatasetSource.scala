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

  // TODO: parallelize by reading from the same file multiple times at different offsets.
  //   This should improve throughput. The current method parallelizing CPU-bound work only ends up using ~40% of CPU.

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

  private def vectors(parallelism: Int, name: String)(implicit ec: ExecutionContext): Source[Dataset.Doc, Future[IOResult]] = {
    val path = Paths.get(directory.toFile.getAbsolutePath, s"$name.${dataset.dtype.extension}")
    val fileIO = FileIO
      .fromPath(path, chunkSize = chunkSize, startPosition = 8)
      .zipWithIndex
    if (parallelism < 2) fileIO.map {
      case (bs, ix) => Dataset.Doc(ix, byteStringToFloatArray(bs))
    }
    else
      fileIO.mapAsyncUnordered(parallelism) {
        case (bs, ix) => Future(Dataset.Doc(ix, byteStringToFloatArray(bs)))
      }
  }

  def baseData(parallelism: Int)(implicit ec: ExecutionContext): Source[Dataset.Doc, Future[IOResult]] =
    vectors(parallelism, "base")

  def sampleData(parallelism: Int)(implicit ec: ExecutionContext): Source[Dataset.Doc, Future[IOResult]] =
    vectors(parallelism, "sample")

  def queryData(parallelism: Int)(implicit ec: ExecutionContext): Source[Dataset.Doc, Future[IOResult]] =
    vectors(parallelism, "query")

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