package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.nio.file.Paths

import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Similarity, Vec}

import zio._
import zio.console._

// TODO: figure out how to provide the Eknn client and Storage as part of the environment.

object UserRepoExample {

  case class UserId(id: Int)
  case class User(id: UserId, name: String)
  case class DBError(t: Throwable) extends Throwable(t)

  type UserRepo = Has[UserRepo.Service]

  object UserRepo {
    trait Service {
      def getUser(userId: UserId): IO[DBError, Option[User]]
      def createUser(user: User): IO[DBError, Unit]
    }
    val inMemory: Layer[Nothing, UserRepo] = ZLayer.succeed(
      new Service {
        def getUser(userId: UserId): IO[DBError, Option[User]] = UIO(???)
        def createUser(user: User): IO[DBError, Unit] = UIO(???)
      }
    )

    def getUser(userId: UserId): ZIO[UserRepo, DBError, Option[User]] =
      ZIO.accessM(_.get.getUser(userId))

    def createUser(user: User): ZIO[UserRepo, DBError, Unit] =
      ZIO.accessM(_.get.createUser(user))
  }

  type Logging = Has[Logging.Service]

  object Logging {
    trait Service {
      def info(s: String): UIO[Unit]
      def error(s: String): UIO[Unit]
    }

    import zio.console.Console
    val consoleLogger: ZLayer[Console, Nothing, Logging] = ZLayer.fromFunction { console =>
      new Service {
        override def info(s: String): UIO[Unit] = console.get.putStrLn(s"info - $s")
        override def error(s: String): UIO[Unit] = console.get.putStrLn(s"error - $s")
      }
    }

    def info(s: String): ZIO[Logging, Nothing, Unit] = ZIO.accessM(_.get.info(s))
    def error(s: String): ZIO[Logging, Nothing, Unit] = ZIO.accessM(_.get.error(s))
  }

  val user: User = User(UserId(123), "Bob")
  val makeUser: ZIO[Logging with UserRepo, DBError, Unit] = for {
    _ <- Logging.info(s"Create user: $user")
    _ <- UserRepo.createUser(user)
    _ <- Logging.info(s"Created user!")
  } yield ()

  val horizontal: ZLayer[Console, Nothing, Logging with UserRepo] = Logging.consoleLogger ++ UserRepo.inMemory
  val full: ZLayer[Any, Nothing, Logging with UserRepo] = Console.live >>> horizontal
  makeUser.provideLayer(full)

}
//trait XModule {
//  val xModule: XModule.Service[Any]
//}
//
//object XModule {
//  case class X(x: Int = 1)
//
//  trait Service[R] {
//    def x: ZIO[R, Nothing, X]
//  }
//
//  trait Live extends XModule {
//    val xInstance: X
//    val xModule: Service[Any] = new Service[Any] {
//      override def x: ZIO[Any, Nothing, X] = UIO(xInstance)
//    }
//  }
//
//  object factory extends Service[XModule] {
//    override def x: ZIO[XModule, Nothing, X] = ZIO.environment[XModule].flatMap(_.xModule.x)
//  }
//
//}

//object DocsExample extends App {
//
//  case class DBConnection()
//
//  case class User(id: Int, name: String)
//
//  def getUser(userId: Int): ZIO[DBConnection, Nothing, Option[User]] = UIO(???)
//  def createUser(user: User): ZIO[DBConnection, Nothing, Unit] = UIO(???)
//
//  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
//    val user: User = User(1234, "Chet")
//    val created: ZIO[DBConnection, Nothing, Boolean] = for {
//      maybeUser <- getUser(user.id)
//      res <- maybeUser.fold(createUser(user).as(true))(_ => ZIO.succeed(false))
//    } yield res
//
//    val dbConn: DBConnection = DBConnection()
//    created
//      .provide(dbConn).fold(_ => 1, _ => 0)
//
//  }
//
//}

//object Driver extends App {
//
//  case class Config(resultsFile: File = new File("/tmp/results.json"))
//
//  private val optionParser = new scopt.OptionParser[Config]("Benchmarks driver") {
//    opt[String]('o', "resultsFile")
//      .action((s, c) => c.copy(resultsFile = new File(s)))
//  }
//
//  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = program
//
//  val logic: ZIO[Console with XModule, Nothing, Int] = (for {
//    _   <- console.putStrLn(s"I'm running!")
//    x   <- XModule.factory.x
//    _   <- console.putStrLn(s"I've got an $x!")
//  } yield 0)
//    .catchAll(e => console.putStrLn(s"Application run failed $e").as(1))
//
//  private val program = logic
//    .provideSome[Console] { c =>
//      new Console with XModule.Live {
//        override val console: Console.Service[Any] = c.console
//        override val xInstance: X                  = X()
//      }
//    }

//  val logic: ZIO[Console with XModule, Nothing, Int] = (for {
//    _   <- console.putStrLn(s"I'm running!")
//    x   <- XModule.factory.x
//    _   <- console.putStrLn(s"I've got an $x!")
//  } yield 0)
//    .catchAll(e => console.putStrLn(s"Application run failed $e").as(1))
//
//  private def program(config: Config) = logic.provideSome[Console] { c =>
//    new Console with XModule.Live {
//      override val console: Console.Service[Any] =
//      override val xInstance: XModule.X = XModule.X()
//    }
//  }
//
//  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
//    optionParser.parse(args, Config()) match {
//      case None => sys.exit(1)
//      case Some(config) => program(config)
//    }

//    val storage = FileStorage.default
//    val stream = storage.streamDataset[Vec.SparseBool](Dataset.AmazonHomePhash)
//    stream
//      .foreach(v => putStrLn(v.toString))
//      .mapError(System.err.println)
//      .fold(_ => 1, _ => 0)

//    storage
//      .saveResult(
//        Result(
//          Dataset.AmazonHomePhash,
//          Mapping.SparseBool(4096),
//          NearestNeighborsQuery.Exact("vec", Vec.Indexed("dummy", "dummy", "dummy"), Similarity.Hamming),
//          10,
//          Seq.empty,
//          Seq.empty
//        ))
//      .fold(_ => 1, _ => 0)
//  }
//}
