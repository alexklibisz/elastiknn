package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api.{ElasticsearchCodec, Vec}
import zio.{Layer, ZLayer}

object DatasetClient {

  trait Service {
    def streamVectors[V <: Vec: ElasticsearchCodec](dataset: Dataset)
  }

  /** Implementation of [[DatasetClient.Service]] that reads from a local directory. */
  def local(datasetsDirectory: File): Layer[Throwable, DatasetClient] = ZLayer.fromFunction { _ =>
    ???
  }

}
