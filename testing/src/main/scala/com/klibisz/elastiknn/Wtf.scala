package com.klibisz.elastiknn

import com.klibisz.elastiknn.elastic4s.ElastiKnnSetupRequest
import com.klibisz.elastiknn.testing.Elastic4sClientSupport
import com.sksamuel.elastic4s.requests.cat.CatNodes
import com.sksamuel.elastic4s.ElasticDsl._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Wtf extends App with Elastic4sClientSupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val run = for {
    res <- client.execute(ElastiKnnSetupRequest())
  } yield res

  println("Starting!")

  val res = try Await.result(run, Duration("10 seconds")) finally client.close()
  println(res)

}
