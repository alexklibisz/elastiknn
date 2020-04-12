package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import io.circe.syntax._
import io.circe.generic.auto._
import play.api.libs.circe.Circe

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

//  case class Bar(bar: Int)
//  case class Foo(foo: String, bar: Bar)
//  def search(datasetName: String, queryId: String) = Action(circe.json[Foo]) { implicit req =>
//    Ok(s"Bye bye $datasetName, ${req.body.foo}, ${req.body.bar}")
//  }

}
