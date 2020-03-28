package com.klibisz.elastiknn.client

import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.bulk.BulkResponse

final class StrictElasticClient(override val client: HttpClient) extends ElasticClient(client) {

  override def execute[T, U, F[_]](t: T)(implicit
                                         executor: Executor[F],
                                         functor: Functor[F],
                                         handler: Handler[T, U],
                                         manifest: Manifest[U]): F[Response[U]] = {
    val request = handler.build(t)
    val f: F[HttpResponse] = executor.exec(client, request)
    functor.map(f) { resp =>
      handler.responseHandler.handle(resp) match {
        case Right(u) => {
          val q = RequestSuccess(resp.statusCode, resp.entity.map(_.content), resp.headers, u)
          q.result match {
            case bulkResponse: BulkResponse =>
          }
          ???
        }
        case Left(error) => {
          RequestFailure(resp.statusCode, resp.entity.map(_.content), resp.headers, error)
        }
      }
    }
  }

}
