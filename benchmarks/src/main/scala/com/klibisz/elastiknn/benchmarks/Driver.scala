//package com.klibisz.elastiknn.benchmarks
//
//import java.io.File
//
//import com.klibisz.elastiknn.api._
//import com.sksamuel.elastic4s.ElasticDsl._
//import zio._
//import zio.clock.Clock
//import zio.console._
//import zio.logging._
//import zio.logging.slf4j.Slf4jLogger
//
//import scala.concurrent.duration._
//import scala.util.hashing.MurmurHash3
//
//object Driver extends App {
//
////  private val vectorField: String = "vec"
////  private val numCores: Int = java.lang.Runtime.getRuntime.availableProcessors()
////
////  private case class SearchResult(neighborIds: Seq[String], duration: Duration)
////
////  private def recalls(exact: Seq[SearchResult], test: Seq[SearchResult]): Seq[Double] = exact.zip(test).map {
////    case (ex, ts) => ex.neighborIds.intersect(ts.neighborIds).length * 1d / ex.neighborIds.length
////  }
////
////  private def deleteIndexIfExists(index: String): ZIO[ElastiknnZioClient, Throwable, Unit] =
////    for {
////      eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
////      req = eknnClient.execute(deleteIndex(index))
////      _ <- req.map(_ => ()).orElse(ZIO.succeed(()))
////    } yield ()
////
////  private def buildIndex(mapping: Mapping,
////                         shards: Int,
////                         dataset: Dataset,
////                         holdoutProportion: Double,
////                         maxDatasetSize: Int,
////                         batchSize: Int = 500): ZIO[Logging with Any with Clock with DatasetClient with ElastiknnZioClient, Throwable, (String, Vector[Vec])] = {
////    val indexName = s"benchmark-${dataset.name}-${MurmurHash3.orderedHash(Seq(mapping, shards, dataset, holdoutProportion, maxDatasetSize, batchSize)).abs}"
////    for {
////      _ <- deleteIndexIfExists(indexName)
////      eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
////      _ <- eknnClient.execute(createIndex(indexName).shards(shards).replicas(0))
////      _ <- eknnClient.putMapping(indexName, vectorField, mapping)
////      datasetClient <- ZIO.access[DatasetClient](_.get)
////      stream = datasetClient.stream[Vec](dataset, Some(maxDatasetSize))
////      holdout <- stream.grouped(batchSize).zipWithIndex.foldM(Vector.empty[Vec]) {
////        case (acc, (vecs, batchIndex)) =>
////          val (holdoutVecs, indexVecs) = vecs.splitAt((vecs.length * holdoutProportion).toInt)
////          val ids: Seq[String] = indexVecs.indices.map(i => s"v$batchIndex-$i")
////          for {
////            (dur, _) <- eknnClient.index(indexName, vectorField, indexVecs, Some(ids)).timed
////            _ <- log.debug(s"Indexed batch $batchIndex containing ${indexVecs.length} vectors in ${dur.toMillis} ms")
////          } yield acc ++ holdoutVecs
////      }
////    } yield (indexName, holdout)
////  }
////
////  private def search(index: String, query: NearestNeighborsQuery, holdout: Vector[Vec], k: Int, par: Int = 1) =
////    for {
////      eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
////      _ <- eknnClient.execute(refreshIndex(index))
////      requests = holdout.map(query.withVec).zipWithIndex.map {
////        case (q, i) =>
////          for {
////            (dur, res) <- eknnClient.nearestNeighbors(index, q, k).timed
////            _ <- log.debug(s"Completed query ${i + 1} of ${holdout.length} in ${dur.toMillis} ms")
////          } yield res
////      }
////      responses <- ZIO.collectAllParN(par)(requests)
////    } yield responses.map(r => SearchResult(r.result.hits.hits.map(_.id).toSeq, r.result.took.millis))
////
////  private def run(experiments: Seq[Experiment], ks: Seq[Int], holdoutProportion: Double, maxDatasetSize: Int) =
////    ZIO.foreach(experiments) { exp =>
////      for {
////        _ <- log.info(s"Starting experiment for dataset ${exp.dataset.name} with ${exp.maqs.length} mappings and ${exp.maqs.map(_.mkQuery.length).sum} total queries")
////        // Create a memoized effect, i.e. it hasn't been executed yet and will only execute once.
////        buildIndexMemo <- buildIndex(exp.exact.mapping, numCores, exp.dataset, holdoutProportion, maxDatasetSize).memoize
////        _ <- ZIO.foreach(ks) { k =>
////          for {
////            holdoutExactResultsMemo <- (for {
////              _ <- log.info(s"Indexing vectors for exact search")
////              (index, holdoutVectors) <- buildIndexMemo
////              _ <- log.info(s"Running exact search")
////              exactResults: Seq[SearchResult] <- search(index, exp.exact.mkQuery.head(vectorField, Vec.Empty(), k), holdoutVectors, k, numCores)
////            } yield (holdoutVectors, exactResults)).memoize
////            testRuns = for {
////              maq <- exp.maqs
////              mkQuery <- maq.mkQuery
////              emptyQuery = mkQuery(vectorField, Vec.Empty(), k)
////              emptyResult = Result(exp.dataset, maq.mapping, emptyQuery, k, Seq.empty, Seq.empty)
////              buildIndexMemoEffect = buildIndex(maq.mapping, exp.shards, exp.dataset, holdoutProportion, maxDatasetSize).memoize
////            } yield
////              for {
////                resultClient <- ZIO.access[ResultClient](_.get)
////                buildIndexMemo <- buildIndexMemoEffect
////                found <- resultClient.find(emptyResult.dataset, emptyResult.mapping, emptyResult.query, emptyResult.k)
////                _ <- if (found.isDefined) log.info(s"Skip test for existing result: $found")
////                else
////                  for {
////                    (index, _) <- buildIndexMemo
////                    (holdoutVectors, exactResults) <- holdoutExactResultsMemo
////                    _ <- log.info(s"Indexing vectors for mapping ${maq.mapping}")
////                    testResults <- search(index, emptyQuery, holdoutVectors, k)
////                    populatedResult: Result = emptyResult.copy(durations = testResults.map(_.duration.toMillis), recalls = recalls(exactResults, testResults))
////                    _ <- resultClient.save(populatedResult)
////                  } yield ()
////              } yield ()
////            _ <- ZIO.collectAll(testRuns)
////          } yield ()
////        }
////      } yield ()
////    }
//
////  sealed trait Options
////  case object Empty extends Options
////  case class Enqueue(resultsLocation: String = "") extends Options
////  case class Execute(resultsLocation: String = "", datasetsLocation: String = "", key: String = "") extends Options
////
////  private val enqueueParser = new scopt.OptionParser[Enqueue]("Build a list of benchmark jobs") {
////    override def showUsageOnError: Option[Boolean] = Some(true)
////    help("help")
////    opt[String]("resultsLocation").action((s, c) => c.copy(resultsLocation = s))
////  }
////
////  private val executeParser = new scopt.OptionParser[Execute]("Execute benchmarks") {
////    override def showUsageOnError: Option[Boolean] = Some(true)
////    help("help")
////    opt[String]("resultsLocation").action((s, c) => c.copy(resultsLocation = s))
////    opt[String]("datasetsLocation").action((s, c) => c.copy(datasetsLocation = s))
////    opt[String]("key").action((s, c) => c.copy(key = s))
////  }
//
//  private val apps = Map[String, zio.App](
//    "enqueue" -> Enqueue,
//    "execute" -> Execute
//  )
//
//  private case class Command(command: String)
//
//  private val parser = new scopt.OptionParser[Command]("Benchmark driver") {
//    override def showUsageOnError: Option[Boolean] = Some(true)
//    help("help")
//    apps.keys.foreach(s => cmd(s).action((_, c) => c.copy(s)))
//  }
//
//  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = parser.parse(args.take(1), Command("")) match {
//    case None                   => sys.exit(1)
//    case Some(Command(command)) => apps(command).run(args.tail)
//  }
//
////  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
////    optionParser.parse(args, Options()) match {
////      case Some(opts) =>
////        val layer =
////          Console.live ++
////            Clock.live ++
////            Slf4jLogger.make((_, s) => s, Some(getClass.getCanonicalName)) ++
////            DatasetClient.local(opts.datasetsDirectory) ++
////            ResultClient.local(opts.resultsFile) ++
////            ElastiknnZioClient.fromFutureClient(opts.elasticsearchHost, opts.elasticsearchPort, strictFailure = true)
////        run(Experiment.defaults.filter(_.dataset == Dataset.AmazonHomePhash), opts.ks, opts.holdoutProportion, opts.maxDatasetSize)
////          .provideLayer(layer)
////          .mapError(System.err.println)
////          .fold(_ => 1, _ => 0)
////      case None => sys.exit(1)
////    }
//}
