import org.elasticsearch.elastiknn.SparseBoolVector
import org.elasticsearch.elastiknn.utils.Implicits._

import scala.util.Random

object Example {
  def main(args: Array[String]): Unit = {
    implicit val rng: Random = new Random()
    val vec = SparseBoolVector.random(10)
    println(vec)
  }
}
