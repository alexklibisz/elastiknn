package com.klibisz.elastiknn.search

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.lucene.scaladsl.LuceneSink
import akka.stream.scaladsl._
import com.klibisz.ann1b.{Dataset, LocalDatasetSource}
import com.klibisz.elastiknn.models.{HashingModel, L2LshModel}
import org.apache.lucene.document.{Document, Field, FieldType}
import org.apache.lucene.index.{IndexOptions, IndexWriterConfig}
import org.apache.lucene.store.MMapDirectory

import java.nio.file.Files
import java.util.Random
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object Utils {
  def indexWithHashingModel(
      model: HashingModel.DenseFloat,
      parallelism: Int,
      vecFieldName: String = "vec",
      idFieldName: String = "id"
  )(implicit ec: ExecutionContext): Flow[Dataset.Doc, Document, NotUsed] = {

    val idFieldType = new FieldType()
    idFieldType.setStored(true)

    val vecFieldType = new FieldType()
    vecFieldType.setStored(false)
    vecFieldType.setOmitNorms(true)
    vecFieldType.setIndexOptions(IndexOptions.DOCS)
    vecFieldType.setTokenized(false)
    vecFieldType.setStoreTermVectors(false)

    Flow[Dataset.Doc]
      .mapAsyncUnordered(parallelism) {
        case Dataset.Doc(id, vec) =>
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

  val dataset = Dataset.bigann
  val parallelism = 8
  val source = LocalDatasetSource(dataset)

  val model = new L2LshModel(dataset.dims, 75, 4, 2, new Random(0))
  val tmpDir = Files.createTempDirectory("elastiknn-lsh-")
  println(s"Indexing to $tmpDir")
  val indexDirectory = new MMapDirectory(tmpDir)
  val indexConfig = new IndexWriterConfig().setMaxBufferedDocs(100000)

  val run = source
    .sampleData(parallelism)
    .take(10000000)
    .runWith(Sink.last)
    .map { doc => println((doc.id, doc.vec.length, doc.vec.toList.take(10))) }
//    .sampleData(parallelism)
//    .take(1000000)
//    .via(Utils.indexWithHashingModel(model, parallelism))
//    .runWith(LuceneSink.create(indexDirectory, indexConfig))

  val t0 = System.nanoTime()
  try Await.result(run, Duration.Inf)
  finally system.terminate()

  println((System.nanoTime() - t0).nanos.toMillis)

}
