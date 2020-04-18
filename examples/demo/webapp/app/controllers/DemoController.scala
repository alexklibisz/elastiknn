package controllers

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.client.ElastiknnFutureClient
import com.klibisz.elastiknn.client.ElastiknnRequests._
import com.sksamuel.elastic4s.ElasticDsl._
import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject._
import models.{Dataset, ExampleWithResults}
import play.api.libs.circe.Circe
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class DemoController @Inject()(val controllerComponents: ControllerComponents, protected val eknn: ElastiknnFutureClient)(
    implicit ec: ExecutionContext)
    extends BaseController
    with Circe {

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def dataset(permalink: String, queryIdOpt: Option[String]): Action[AnyContent] = Action.async { implicit req =>
    Dataset.defaults.find(_.permalink == permalink) match {
      case Some(ds) =>
        queryIdOpt match {
          case Some(queryId) =>
            for {
              examplesWithResults <- Future.traverse(ds.examples) { ex =>
                val q = nearestNeighborsQuery(ex.index, ex.query.withVec(Vec.Indexed(ex.index, queryId, ex.field)), 10, true)
                val t0 = System.currentTimeMillis()
                for {
                  response <- eknn.execute(q)
                  dur = System.currentTimeMillis() - t0
                  hits = response.result.hits.hits.toSeq
                  results <- Future.traverse(hits.map(ds.parseHit))(Future.fromTry)
                } yield ExampleWithResults(ex, q, results, dur)
              }
            } yield Ok(views.html.dataset(ds, queryId, examplesWithResults))
          case None =>
            for {
              countRes <- eknn.execute(count(ds.examples.head.index))
              id = Random.nextInt(countRes.result.count.toInt) + 1
            } yield Redirect(routes.DemoController.dataset(permalink, Some(id.toString)))
        }

      case None => Future.successful(NotFound(views.html.notfound()))
    }
  }

  def datasets(): Action[AnyContent] = Action(Ok(Dataset.defaults.asJson))

}
