import com.klibisz.elastiknn.SparseBoolVector
import com.klibisz.elastiknn.client.ElastiKnnClient

import scala.concurrent._

/**
 * For now this example just demonstrates how to import a couple things published
 * by the core and client projects. In the future it might do something more useful,
 * like storing some vectors and running a search.
 */
object Example {
  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val eknn = ElastiKnnClient("localhost", 9200)
    println(eknn)
    eknn.close()
    val vec = SparseBoolVector(Array(1,2,3), 10)
    println(vec)
  }
}
