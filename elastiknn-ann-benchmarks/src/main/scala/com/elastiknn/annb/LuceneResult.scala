package com.elastiknn.annb

import scala.concurrent.duration.Duration

final case class LuceneResult(time: Duration, neighbors: Array[Int], distances: Array[Float])
