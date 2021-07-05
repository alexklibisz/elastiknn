package com.klibisz.elastiknn.search

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import com.klibisz.ann1b.{Dataset, LocalDatasetSource}
import com.klibisz.elastiknn.models.{HashingModel, L2LshModel}
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.store.{FSDirectory, MMapDirectory}

import java.nio.file.Files
import java.util
import java.util.Random
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object Utils {
  def indexWithHashingModel(
      model: HashingModel.DenseFloat,
      parallelism: Int,
      vecFieldName: String = "vec",
      idFieldName: String = "id",
      indexWriter: IndexWriter
  )(implicit ec: ExecutionContext): Sink[Dataset.Doc, Future[Done]] = {

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
            val hashes = model.hash(vec)
            val fields = new util.ArrayList[Field](hashes.length + 1)
            fields.add(new Field(idFieldName, id, idFieldType))
            hashes.foreach { hf => fields.add(new Field(vecFieldName, hf.hash, vecFieldType)) }
            indexWriter.addDocument(fields)
          }
      }
      .toMat(Sink.ignore) {
        case (_: NotUsed, f: Future[Done]) =>
          f.andThen {
            case _ =>
              println(s"Closing $indexWriter")
              indexWriter.close()
          }
      }
  }
}

object BigAnnChallenge extends App {

  implicit val executionContext = ExecutionContext.global
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer

  val dataset = Dataset.bigann
  val numProcessors = Runtime.getRuntime.availableProcessors()
  val parallelism = 2 * numProcessors
  val source = LocalDatasetSource(dataset)

  val model = new L2LshModel(dataset.dims, 75, 4, 2, new Random(0))
  val tmpDir = Files.createTempDirectory("elastiknn-lsh-")
  println(tmpDir)

  val indexDirectory = new MMapDirectory(tmpDir)

  val indexConfig = new IndexWriterConfig()
    .setMaxBufferedDocs(Int.MaxValue)
    .setRAMBufferSizeMB(Double.MaxValue)
    .setMergePolicy(NoMergePolicy.INSTANCE)
    .setRAMPerThreadHardLimitMB(1024 / numProcessors)

  val indexWriter = new IndexWriter(indexDirectory, indexConfig)

  val run = source
    .sampleData(parallelism)
    .take(1000000)
    .runWith(Utils.indexWithHashingModel(model, parallelism, indexWriter = indexWriter))

  val t0 = System.nanoTime()
  try Await.result(run, Duration.Inf)
  finally system.terminate()

  println((System.nanoTime() - t0).nanos.toSeconds)

  val indexReader = DirectoryReader.open(indexDirectory)
  println(indexReader.leaves.size())

}
