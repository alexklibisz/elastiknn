package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api.Vec
import zio._
import zio.console._

object Driver extends App {
  override def run(args: List[String]): URIO[Console, Int] = {
    val storage = new FileStorage(new File("/home/alex/.elastiknn-data"), new File("/tmp"))
    val stream = storage.streamDataset[Vec.SparseBool](Dataset.AmazonHomePhash)
    stream
      .foreach(v => putStrLn(v.toString))
      .mapError(System.err.println)
      .fold(_ => 1, _ => 0)
  }
}
