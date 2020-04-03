import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.client.ElastiknnClient

import scala.concurrent._

/**
 * For now this example just demonstrates how to import a couple things published
 * by the core and client projects. In the future it might do something more useful,
 * like storing some vectors and running a search.
 */
object Example {
  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val eknn = ElastiknnClient.futureClient()
    println(eknn)
    eknn.close()
    val vec = Vec.SparseBool(Array(1,2,3), 10)
    println(vec)
  }
}
