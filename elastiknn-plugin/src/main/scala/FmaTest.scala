import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.Utils
import jdk.incubator.vector.{FloatVector, VectorOperators}

import scala.util.Random

object FmaTest extends App {

  implicit val rng = new Random(0)

  def time[T](s: String, n: Int)(op: () => T): Unit = {
    val t0 = System.nanoTime()
    for (_ <- 1 to n) op()
    val t1 = System.nanoTime()
    println(s"${s} ${t1 - t0}")
  }

  val v1 = Vec.DenseFloat.random(3333).values
  val v2 = Vec.DenseFloat.random(3333).values
  val n = 1000000

  val species = FloatVector.SPECIES_256
  var sum = 0f
  var i = 0
  while (i < v1.length) {
    val m = species.indexInRange(i, v1.length)
    val l = FloatVector.fromArray(species, v1, i, m)
    val r = FloatVector.fromArray(species, v2, i, m)
    sum += l.mul(r).reduceLanes(VectorOperators.ADD)
    i += species.length()
  }
  println(Utils.dot(v1, v2))
  println(Utils.dotFma(v1, v2))
  println(Utils.dotPanama(v1, v2))

  for (i <- 1 to 10) {
    time("dot   ", n)(() => Utils.dot(v1, v2))
    time("dotFma", n)(() => Utils.dotFma(v1, v2))
    time("panama", n)(() => Utils.dotPanama(v1, v2))
    println("---")
  }
}
