package com.klibisz.elastiknn.search

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.alpakka.lucene.scaladsl.LuceneSink
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, IOResult}
import com.klibisz.elastiknn.models.{HashingModel, L2LshModel}
import org.apache.lucene.document.{Document, Field, FieldType}
import org.apache.lucene.index.{IndexOptions, IndexWriterConfig}
import org.apache.lucene.store.MMapDirectory

import java.nio.ByteOrder
import java.nio.file.{Files, Path, Paths}
import java.util.Random
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

sealed trait Datatype {
  def chunkSize: Int
}
object Datatype {
  case object U8Bin extends Datatype {
    override def chunkSize: Int = 1
  }
  case object I8Bin extends Datatype {
    override def chunkSize: Int = 1
  }
  case object FBin extends Datatype {
    override def chunkSize: Int = 4
  }
}

final case class Dataset(path: Path, dims: Int, dtype: Datatype)

object Dataset {
  val sift1b = Dataset(Paths.get("/home/alex/Desktop/big-ann-benchmarks/bigann/learn.100M.u8bin"), 128, Datatype.U8Bin)
  val space1b = Dataset(Paths.get("/home/alex/Desktop/big-ann-benchmarks/microsoft-spacev1b/learn.100M.i8bin"), 100, Datatype.I8Bin)
  val yandex1b = Dataset(Paths.get("/home/alex/Desktop/big-ann-benchmarks/yandex-text-to-image-1b/learn.50M.fbin"), 200, Datatype.FBin)
}

case class VecDoc(docId: String, values: Array[Float])

object Utils {

  /**
    * Read a dataset from disk.
    */
  def streamDataset(dataset: Dataset, parallelism: Int)(implicit ec: ExecutionContext): Source[VecDoc, Future[IOResult]] = {
    dataset.dtype match {
      case Datatype.U8Bin =>
        FileIO
          .fromPath(dataset.path, chunkSize = dataset.dims * dataset.dtype.chunkSize, startPosition = 8)
          .zipWithIndex
          .mapAsyncUnordered(parallelism) {
            case (bs, ix) =>
              Future {
                val arr = new Array[Byte](dataset.dims)
                bs.asByteBuffer.get(arr, 0, arr.length)
                VecDoc(s"v$ix", arr.map(b => (b.toInt & 0xFF).toFloat))
              }
          }
      case Datatype.I8Bin =>
        FileIO
          .fromPath(dataset.path, chunkSize = dataset.dims * dataset.dtype.chunkSize, startPosition = 8)
          .zipWithIndex
          .mapAsyncUnordered(parallelism) {
            case (bs, ix) =>
              Future {
                val arr = new Array[Byte](dataset.dims)
                bs.asByteBuffer.get(arr, 0, arr.length)
                VecDoc(s"v$ix", arr.map(_.toFloat))
              }
          }
      case _ =>
        FileIO
          .fromPath(dataset.path, chunkSize = dataset.dims * dataset.dtype.chunkSize, startPosition = 8)
          .zipWithIndex
          .mapAsyncUnordered(parallelism) {
            case (bs, ix) =>
              Future {
                val arr = new Array[Float](dataset.dims)
                val buf = bs.asByteBuffer.alignedSlice(4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                buf.get(arr, 0, arr.length)
                VecDoc(s"v$ix", arr)
              }
          }
    }
  }

  def indexWithHashingModel(
      model: HashingModel.DenseFloat,
      parallelism: Int,
      vecFieldName: String = "vec",
      idFieldName: String = "id"
  )(implicit ec: ExecutionContext): Flow[VecDoc, Document, NotUsed] = {

    val idFieldType = new FieldType()
    idFieldType.setStored(true)

    val vecFieldType = new FieldType()
    vecFieldType.setOmitNorms(true)
    vecFieldType.setIndexOptions(IndexOptions.DOCS)
    vecFieldType.setTokenized(false)
    vecFieldType.setStoreTermVectors(false)

    Flow[VecDoc]
      .mapAsyncUnordered(parallelism) {
        case VecDoc(id, vec) =>
          Future {
            val d = new Document()
            d.add(new Field(idFieldName, id, idFieldType))
            model.hash(vec).foreach(hf => d.add(new Field(vecFieldName, hf.hash, vecFieldType)))
            d
          }
      }
  }
}

object BigAnnChallenge extends App {

  implicit val executionContext = ExecutionContext.global
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer

  val parallelism = 8
  val dataset = Dataset.space1b

  val model = new L2LshModel(dataset.dims, 40, 3, 5, new Random(0))

  val tmpDir = Files.createTempDirectory("elastiknn-lsh-")
  val indexDirectory = new MMapDirectory(tmpDir)
  val indexConfig = new IndexWriterConfig().setMaxBufferedDocs(100000)

  val run = Utils
    .streamDataset(dataset, parallelism)
    //    .take(1000000)
    .via(Utils.indexWithHashingModel(model, parallelism))
    .runWith(LuceneSink.create(indexDirectory, indexConfig))

  try Await.result(run, Duration.Inf)
  finally system.terminate()
}
