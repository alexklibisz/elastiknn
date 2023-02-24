import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.{ExactModel, VectorUtils}

import scala.util.Random

object PanamaTest extends App {

  implicit val rng = new Random(0)

  Thread.sleep(10000)

  def time[T](s: String, n: Int)(op: () => T): Unit = {
    val t0 = System.nanoTime()
    for (_ <- 1 to n) op()
    val t1 = System.nanoTime()
    println(s"${s} ${t1 - t0}")
  }

  val v1 = Vec.DenseFloat.random(4096).values
  val v2 = Vec.DenseFloat.random(4096).values
  val n = 1000000

  println(VectorUtils.dotFma(v1, v2))
  println(VectorUtils.dotProduct(v1, v2))

  val l2m = new ExactModel.L2()

  for (_ <- 1 to 10) {
//    time("dot  ", n)(() => VectorUtils.dotFma(v1, v2))
//    time("dotP ", n)(() => VectorUtils.dotProduct(v1, v2))
    time("l2   ", n)(() => l2m.similarity(v1, v2))
    time("l2P  ", n)(() => VectorUtils.euclideanDistance(v1, v2))
    println("---")
  }
}
