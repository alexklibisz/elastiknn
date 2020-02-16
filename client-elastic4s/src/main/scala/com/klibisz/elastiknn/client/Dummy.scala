package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.{JaccardLshOptions, ProcessorOptions}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Dummy {

  def main(args: Array[String]): Unit = {

    implicit val ec: ExecutionContext = ExecutionContext.global
    val client = ElastiKnnClient()
    val req =
      client.prepareMapping("testing", ProcessorOptions("raw", 100, ModelOptions.Jaccard(JaccardLshOptions(fieldProcessed = "proc"))))
    try Await.result(req, Duration("2 seconds"))
    finally client.close()

  }

}
