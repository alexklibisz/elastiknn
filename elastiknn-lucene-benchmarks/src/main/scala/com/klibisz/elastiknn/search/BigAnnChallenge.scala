package com.klibisz.elastiknn.search

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.alpakka.lucene.scaladsl.LuceneSink
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, IOResult, Materializer}
import com.klibisz.elastiknn.models.{HashingModel, L2LshModel}
import org.apache.lucene.document.{Document, Field, FieldType}
import org.apache.lucene.index.{IndexOptions, IndexWriterConfig}
import org.apache.lucene.store.MMapDirectory

import java.nio.ByteOrder
import java.nio.file.{Files, Path, Paths}
import java.util.Random
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

sealed trait Datatype
object Datatype {
  case object U8Bin extends Datatype
  case object FBin extends Datatype
  case object I8Bin extends Datatype
}

case class Dataset(path: Path, dims: Int, dtype: Datatype)

object Dataset {
  val sift1b = Dataset(Paths.get("/home/alex/Desktop/big-ann-benchmarks/bigann/learn.100M.u8bin"), 128, Datatype.U8Bin)
  val space1b = Dataset(Paths.get("/home/alex/Desktop/big-ann-benchmarks/microsoft-spacev1b/learn.100M.i8bin"), 100, Datatype.I8Bin)
  val yandex1b = Dataset(Paths.get("/home/alex/Desktop/big-ann-benchmarks/yandex-text-to-image-1b/learn.50M.fbin"), 200, Datatype.FBin)
}

case class VecDoc(docId: String, values: Array[Float])

object Streaming {

  /**
    * Read a dataset from disk.
    * @return Source of (vector, ID) tuples.
    */
  def streamDataset(dataset: Dataset)(implicit mat: Materializer): Source[VecDoc, Future[IOResult]] = {
    dataset.dtype match {
      case Datatype.U8Bin =>
        FileIO
          .fromPath(dataset.path, chunkSize = dataset.dims, startPosition = 8)
          .zipWithIndex
          .map {
            case (bs, ix) => VecDoc(s"v$ix", bs.map(b => (b.toInt & 0xFF).toFloat).toArray)
          }
      case Datatype.I8Bin =>
        FileIO
          .fromPath(dataset.path, chunkSize = dataset.dims, startPosition = 8)
          .zipWithIndex
          .map {
            case (bs, ix) => VecDoc(s"v$ix", bs.map(_.toInt.toFloat).toArray)
          }
      case _ =>
        FileIO
          .fromPath(dataset.path, chunkSize = dataset.dims * 4, startPosition = 8)
          .zipWithIndex
          .map {
            case (bs, ix) =>
              VecDoc(s"v$ix", bs.grouped(4).toArray.map(_.asByteBuffer.order(ByteOrder.LITTLE_ENDIAN).getFloat))
          }
    }
  }

  def indexWithHashingModel(
      model: HashingModel.DenseFloat,
      vecFieldName: String = "vec",
      idFieldName: String = "id"
  ): Flow[VecDoc, Document, NotUsed] = {

    val idFieldType = new FieldType()
    idFieldType.setStored(true)

    val vecFieldType = new FieldType()
    vecFieldType.setOmitNorms(true)
    vecFieldType.setIndexOptions(IndexOptions.DOCS)
    vecFieldType.setTokenized(false)
    vecFieldType.setStoreTermVectors(false)

    Flow[VecDoc]
      .map {
        case VecDoc(id, vec) =>
          val d = new Document()
          d.add(new Field(idFieldName, id, idFieldType))
          model.hash(vec).foreach(hf => d.add(new Field(vecFieldName, hf.hash, vecFieldType)))
          d
      }
  }
}

object BigAnnChallenge extends App {

  implicit val executionContext = ExecutionContext.global
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer

  val dataset = Dataset.yandex1b
  val model = new L2LshModel(dataset.dims, 40, 3, 5, new Random(0))
  val tmpDir = Files.createTempDirectory("elastiknn-lsh-")
  val indexDirectory = new MMapDirectory(tmpDir)
  val indexConfig = new IndexWriterConfig().setMaxBufferedDocs(100000)

  println(tmpDir)

  val run = Streaming
    .streamDataset(dataset)
    .via(Streaming.indexWithHashingModel(model))
    .runWith(LuceneSink.create(indexDirectory, indexConfig))

  try Await.result(run, Duration.Inf)
  finally system.terminate()
}
