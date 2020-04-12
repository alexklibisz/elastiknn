package controllers

import javax.inject._
import play.api.mvc._
import io.circe.syntax._
import models.Dataset
import play.api.libs.circe.Circe
import io.circe.generic.auto._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class DemoController @Inject()(val controllerComponents: ControllerComponents) extends BaseController with Circe {

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def dataset(datasetId: String, queryIdOpt: Option[String]) = Action { implicit request: Request[AnyContent] =>
    Ok(s"Render view for dataset $datasetId, ${queryIdOpt.map(id => s"query id $id").getOrElse("random query")}")
  }

  def datasets(): Action[AnyContent] = Action(Ok(Dataset.defaults.asJson))

}
