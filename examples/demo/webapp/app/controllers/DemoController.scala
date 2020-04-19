package controllers

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.client.ElastiknnFutureClient
import com.klibisz.elastiknn.client.ElastiknnRequests._
import com.sksamuel.elastic4s.ElasticDsl._
import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject._
import models.{Dataset, ExampleWithResults}
import play.api.Logging
import play.api.libs.circe.Circe
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class DemoController @Inject()(val controllerComponents: ControllerComponents, protected val eknn: ElastiknnFutureClient)(
    implicit ec: ExecutionContext)
    extends BaseController
    with Logging
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
              countRes <- eknn.execute(count(ds.examples.head.index))
              // This ensures the search requests execute serially.
              examplesWithResults <- ds.examples.foldLeft(Future(Vector.empty[ExampleWithResults])) {
                case (accF, ex) =>
                  for {
                    acc <- accF
                    q = nearestNeighborsQuery(ex.index, ex.query.withVec(Vec.Indexed(ex.index, queryId, ex.field)), 10, true)
                    response <- eknn.execute(q)
                    hits = response.result.hits.hits.toSeq
                    results <- Future.traverse(hits.map(ds.parseHit))(Future.fromTry)
                  } yield acc :+ ExampleWithResults(ex, q, results, response.result.took)
              }
            } yield Ok(views.html.dataset(ds, queryId, countRes.result.count, examplesWithResults))
          case None =>
            for {
              countRes <- eknn.execute(count(ds.examples.head.index))
              id = Random.nextInt(countRes.result.count.toInt + 1)
            } yield Redirect(routes.DemoController.dataset(permalink, Some(id.toString)))
        }

      case None => Future.successful(NotFound(views.html.notfound()))
    }
  }

  def datasets(): Action[AnyContent] = Action(Ok(Dataset.defaults.asJson))

  def health(): Action[AnyContent] = Action.async { implicit req =>
    for {
      countResults <- Future.sequence(for {
        ds <- Dataset.defaults
        ex <- ds.examples
      } yield eknn.execute(count(ex.index)))
      code = if (countResults.forall(_.isSuccess) && countResults.forall(_.result.count > 1000)) 200 else 503
    } yield Status(code)
  }

}
