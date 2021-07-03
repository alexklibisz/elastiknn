package com.klibisz.ann1b

import akka.NotUsed
import akka.stream.scaladsl._
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString

import java.nio.ByteOrder
import java.nio.file.{Path, Paths}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

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

  private val numBytesInVector: Int = dataset.dims * dataset.dtype.sizeOf

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

  private def vectors(
      numPartitions: Int,
      name: String
  )(implicit mat: Materializer, ec: ExecutionContext): Source[Dataset.Doc, NotUsed] = {
    val path = Paths.get(directory.toFile.getAbsolutePath, s"$name.${dataset.dtype.extension}")

    val (numVecsTotal, _) = Await.result(
      FileIO
        .fromPath(path, 8, 0)
        .take(1)
        .map { bs =>
          val buf = bs.asByteBuffer.alignedSlice(4).order(ByteOrder.LITTLE_ENDIAN)
          (buf.getInt(), buf.getInt())
        }
        .runWith(Sink.head),
      1.second
    )

    val numVecsInPartition = numVecsTotal.toLong / numPartitions
    val numBytesInPartition = numVecsInPartition * numBytesInVector

    val sources = (0 until numPartitions)
      .map { p =>
        FileIO
          .fromPath(path, chunkSize = numBytesInVector, startPosition = 8 + p * numBytesInPartition)
          .take(numVecsInPartition)
          .zipWithIndex
          .map {
            case (bs, ix) => Dataset.Doc(ix + p * numVecsInPartition, byteStringToFloatArray(bs))
          }
      }

    val combined =
      if (sources.length == 1) Source.combine(sources.head, Source.empty)(Merge(_))
      else if (sources.length == 2) Source.combine(sources.head, sources.last)(Merge(_))
      else Source.combine(sources.head, sources.tail.head, sources.tail.tail: _*)(Merge(_))

    combined
  }

  def baseData(partitions: Int)(implicit mat: Materializer, ec: ExecutionContext): Source[Dataset.Doc, NotUsed] =
    vectors(partitions, "base")

  def sampleData(partitions: Int)(implicit mat: Materializer, ec: ExecutionContext): Source[Dataset.Doc, NotUsed] =
    vectors(partitions, "sample")

  def queryData(partitions: Int)(implicit mat: Materializer, ec: ExecutionContext): Source[Dataset.Doc, NotUsed] =
    vectors(partitions, "query")

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
