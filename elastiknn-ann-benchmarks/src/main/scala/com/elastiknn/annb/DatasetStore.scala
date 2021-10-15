package com.elastiknn.annb

import akka.NotUsed
import akka.stream.scaladsl.{FileIO, Merge, Source}
import akka.util.ByteString
import com.klibisz.elastiknn.api.Vec

import java.io.FileInputStream
import java.nio.file.Path
import java.nio.{ByteBuffer, ByteOrder}

/**
  * DatasetStore provides an interface to stream vectors of a specific type from disk
  */
trait DatasetStore[V <: Vec] {
  def indexVectors(parallelism: Int, dataset: Dataset[V]): Source[(V, Long), NotUsed]
}

object DatasetStore {

  private object BigAnnBenchmarks {

    object Sizes {
      val fbin = 4
    }

    def readFBin(dims: Int): (Int, ByteString) => Vec.DenseFloat = (_: Int, bs: ByteString) => {
      val arr = new Array[Float](dims)
      val buf = bs.asByteBuffer.alignedSlice(4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
      buf.get(arr, 0, arr.length)
      Vec.DenseFloat(arr)
    }

    def readVectors[V <: Vec.KnownDims](
        path: Path,
        parallelism: Int,
        numBytesInVector: Int,
        bytesToVector: (Int, ByteString) => V
    ): Source[(V, Long), NotUsed] = {
      val numVecsTotal = {
        val fin = new FileInputStream(path.toFile)
        val buf =
          try fin.readNBytes(4)
          finally fin.close()
        ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getInt
      }
      val numVecsInPartition = numVecsTotal.toLong / parallelism
      val numBytesInPartition = numVecsInPartition * numBytesInVector
      val sources = (0 until parallelism)
        .map { p =>
          val startIx = p * numVecsInPartition
          val docs = FileIO
            .fromPath(path, chunkSize = numBytesInVector, startPosition = 8 + p * numBytesInPartition)
            .zipWithIndex
            .map {
              case (bs: ByteString, ix: Long) => (bytesToVector(p, bs), startIx + ix)
            }
          if (p < parallelism - 1) docs.take(numVecsInPartition) else docs
        }
      val combined =
        if (sources.length == 1) Source.combine(sources.head, Source.empty)(Merge(_))
        else if (sources.length == 2) Source.combine(sources.head, sources.last)(Merge(_))
        else Source.combine(sources.head, sources.tail.head, sources.tail.tail: _*)(Merge(_))
      combined
    }
  }

  /**
    * Client that reads datasets in the big-ann-benchmarks binary format.
    */
  final class BigAnnBenchmarksDenseFloat(datasetsPath: Path) extends DatasetStore[Vec.DenseFloat] {
    override def indexVectors(
        parallelism: Int,
        dataset: Dataset[Vec.DenseFloat]
    ): Source[(Vec.DenseFloat, Long), NotUsed] =
      BigAnnBenchmarks.readVectors[Vec.DenseFloat](
        datasetsPath.resolve(dataset.indexFilePath),
        parallelism,
        dataset.dims * BigAnnBenchmarks.Sizes.fbin,
        BigAnnBenchmarks.readFBin(dataset.dims)
      )
  }
}
