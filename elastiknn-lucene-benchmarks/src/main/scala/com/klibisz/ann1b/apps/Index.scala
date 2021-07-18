package com.klibisz.ann1b.apps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.klibisz.ann1b._
import com.klibisz.elastiknn.models.L2LshModel
import com.typesafe.scalalogging.StrictLogging

import java.nio.file.Files
import java.util.Random
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object Index extends App with StrictLogging {

  implicit val executionContext = ExecutionContext.global
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer

  val parallelism = 2 * Runtime.getRuntime.availableProcessors()
  val dataset = Dataset.bigann
  val source = LocalDatasetSource(dataset)
  val model = new L2LshModel(dataset.dims, 75, 4, 2, new Random(0))
  val tmpDir = Files.createTempDirectory("elastiknn-lsh-")

  val luceneModel = new ElastiknnLshLuceneModel(model)
  val run = source
    .sampleData(parallelism)
    .take(1000000)
    .runWith(luceneModel.index(tmpDir, parallelism))

  val t0 = System.nanoTime()
  try Await.result(run, Duration.Inf)
  finally system.terminate()
}
