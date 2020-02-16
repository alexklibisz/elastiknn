package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.{JaccardLshOptions, ProcessorOptions}

import scala.concurrent.{Await, ExecutionContext}

object Dummy {

  def main(args: Array[String]): Unit = {

    implicit val ec: ExecutionContext = ExecutionContext.global
    val client = ElastiKnnClient()


    Await.result(client.prepareMapping("foo", ProcessorOptions("raw", 100, ModelOptions(ModelOptions.Jaccard())))

  }

}
