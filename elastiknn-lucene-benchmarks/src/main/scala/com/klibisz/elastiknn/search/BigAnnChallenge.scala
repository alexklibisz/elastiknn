package com.klibisz.elastiknn.search

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.klibisz.ann1b.{Dataset, LocalDatasetSource, LuceneModel}
import com.klibisz.elastiknn.models.L2LshModel
import org.apache.lucene.index._
import org.apache.lucene.store.MMapDirectory

import java.nio.file.Files
import java.util.Random
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object BigAnnChallenge extends App {

  implicit val executionContext = ExecutionContext.global
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer

  val parallelism = 2 * Runtime.getRuntime.availableProcessors()
  val dataset = Dataset.bigann
  val source = LocalDatasetSource(dataset)
  val model = new L2LshModel(dataset.dims, 75, 4, 2, new Random(0))
  val tmpDir = Files.createTempDirectory("elastiknn-lsh-")

  val luceneModel = new LuceneModel.ElastiknnLsh(model)
  val run: Future[Done] = source
    .sampleData(parallelism)
    .runWith(luceneModel.index(tmpDir, parallelism))

  val t0 = System.nanoTime()
  try Await.result(run, Duration.Inf)
  finally system.terminate()

  println((System.nanoTime() - t0).nanos.toSeconds)

  val indexReader = DirectoryReader.open(new MMapDirectory(tmpDir))
  println(indexReader.leaves.size())

}
