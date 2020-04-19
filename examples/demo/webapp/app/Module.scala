import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.{AbstractModule, TypeLiteral}
import com.klibisz.elastiknn.client.{ElastiknnClient, ElastiknnFutureClient}
import javax.inject.Provider
import play.api.{Configuration, Environment}

import scala.concurrent.ExecutionContext

class Module(environment: Environment, configuration: Configuration) extends AbstractModule {

  val eknnProvider = new Provider[ElastiknnFutureClient] {
    override def get(): ElastiknnFutureClient = {
      val tfac: ThreadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat("elastiknn-%d").build()
      val exec: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors(), tfac)
      implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(exec)
      val host = configuration.underlying.getString("elastiknn.elasticsearch.host")
      val port = configuration.underlying.getInt("elastiknn.elasticsearch.port")
      ElastiknnClient.futureClient(host, port)
    }
  }

  override def configure(): Unit = {
    // Weird that you have to use this constructor, but it works.
    bind(new TypeLiteral[ElastiknnFutureClient]() {}).toProvider(eknnProvider)
  }
}
