package com.klibisz.elastiknn.benchmarks

import zio._
import zio.console._

object Execute extends App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = putStrLn(args.mkString).map(_ => 0)
}
