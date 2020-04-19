package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  *
  * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
  */
class DemoControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {}
