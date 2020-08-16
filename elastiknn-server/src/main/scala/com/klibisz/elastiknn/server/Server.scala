package com.klibisz.elastiknn.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object Server {

  def elasticRoutes: Route = concat(
    pathPrefix(Segment) { index: String =>
      concat(
        path("_mapping") {
          (pathEnd & put) {
            complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"PUT $index/_mapping")))
          }
        },
        path("_search") {
          (pathEnd & post) {
            complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST $index/_search")))
          }
        },
        (pathEnd & post) {
          complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST $index")))
        }
      )
    },
    (path("_bulk") & post) {
      complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST _bulk")))
    }
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("elastiknn-server")
    val server = Http().newServerAt("localhost", 8080)
    server.bind(elasticRoutes)
  }

}
