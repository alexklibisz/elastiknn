package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api.Vec
import zio._
import zio.console._

object Driver extends App {
  override def run(args: List[String]): URIO[Console, Int] = {
    val storage = new FileStorage(new File("/tmp"), new File("/tmp/foo.json"))
    val stream = storage.streamDataset[Vec.DenseFloat](Dataset.AmazonHome)
    stream
      .foreach(v => putStrLn(v.toString))
      .mapError(System.err.println)
      .fold(_ => 1, _ => 0)
  }
}
