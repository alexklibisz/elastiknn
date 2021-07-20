package com.klibisz.ann1b.apps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.klibisz.ann1b.apps.Index.dataset
import com.klibisz.ann1b.{Dataset, LocalDatasetSource}
import com.klibisz.elastiknn.models.L2LshModel
import com.typesafe.scalalogging.StrictLogging

import java.nio.file.Paths
import java.util.Random
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Search extends App with StrictLogging {

  implicit val executionContext = ExecutionContext.global
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer

  val parallelism = 2 * Runtime.getRuntime.availableProcessors()
  val dataset = Dataset.bigann
  val source = LocalDatasetSource(dataset)
  val model = new L2LshModel(dataset.dims, 75, 4, 2, new Random(0))
  val indexPath = Paths.get("/tmp/elastiknn-lsh-bigann")

  val run = source
    .queryData(parallelism)
    .take(10)
    .map(_.vec.toList)
    .runForeach(println)

  val t0 = System.nanoTime()
  try Await.result(run, Duration.Inf)
  finally system.terminate()

}
