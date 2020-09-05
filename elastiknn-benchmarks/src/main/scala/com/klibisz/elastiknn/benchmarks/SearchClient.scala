package com.klibisz.elastiknn.benchmarks

import java.net.URI
import java.util.concurrent.TimeUnit

import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Vec}
import com.klibisz.elastiknn.client.{ElastiknnClient, ElastiknnRequests}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.HealthStatus
import com.sksamuel.elastic4s.{ElasticDsl, Handler, Response}
import org.apache.lucene.codecs.lucene84.Lucene84Codec
import org.apache.lucene.codecs.memory.{DirectDocValuesFormat, DirectPostingsFormat}
import org.apache.lucene.codecs.{DocValuesFormat, PostingsFormat}
import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.logging._
import zio.stream._

import scala.concurrent.Future

trait SearchClient {
  def blockUntilReady(): ZIO[Clock, Throwable, Unit]
  def indexExists(index: String): Task[Boolean]
  def buildIndex(index: String, mapping: Mapping, shards: Int, vectors: Stream[Throwable, Vec]): ZIO[Logging with Clock, Throwable, Long]
  def search(index: String,
             queries: Stream[Throwable, NearestNeighborsQuery],
             k: Int,
             par: Int): ZStream[Logging with Clock, Throwable, QueryResult]
  def deleteIndex(index: String): Task[Unit]
  def close(): Task[Unit]
}

object SearchClient {

  def elasticsearch(uri: URI, strictFailure: Boolean, timeoutMillis: Int): ZLayer[Any, Throwable, Has[SearchClient]] = {
    ZLayer.fromEffect {
      ZIO.fromFuture { implicit ec =>
        Future {
          new SearchClient {

            private val client = ElastiknnClient.futureClient(uri.getHost, uri.getPort, strictFailure, timeoutMillis)

            private def execute[T, U](t: T)(implicit handler: Handler[T, U], manifest: Manifest[U]): Task[Response[U]] =
              Task.fromFuture(_ => client.execute(t))

            def blockUntilReady(): ZIO[Clock, Throwable, Unit] = {
              val check = clusterHealth.waitForStatus(HealthStatus.Yellow).timeout("90s")
              val sched = Schedule.recurs(10) && Schedule.spaced(Duration(10, TimeUnit.SECONDS))
              execute(check).retry(sched).map(_ => ())
            }

            def indexExists(index: String): Task[Boolean] =
              execute(ElasticDsl.indexExists(index)).map(_.result.exists).catchSome {
                case _: ElastiknnClient.StrictFailureException => ZIO.succeed(false)
                case _: RuntimeException                       => ZIO.succeed(false)
              }

            def buildIndex(index: String,
                           mapping: Mapping,
                           shards: Int,
                           vectors: Stream[Throwable, Vec]): ZIO[Logging with Clock, Throwable, Long] = {
              {
                for {
                  _ <- log.info(s"Creating index [$index] with [0] replicas and [$shards] shards")
                  _ <- indexExists(index)
                  _ <- execute(
                    createIndex(index)
                      .replicas(0)
                      .shards(shards)
                      .indexSetting("refresh_interval", "-1"))
                  _ <- execute(ElastiknnRequests.putMapping(index, "vec", "id", mapping))
                  n <- vectors
                    .grouped(200)
                    .zipWithIndex
                    .foldM(0L) {
                      case (n, (vecs, batchIndex)) =>
                        val ids = vecs.indices.map(i => s"$batchIndex-$i")
                        for {
                          (dur, _) <- Task.fromFuture(_ => client.index(index, "vec", vecs, "id", ids)).timed
                          _ <- if (batchIndex % 100 == 0)
                            log.debug(s"Indexed batch [$batchIndex] with [${vecs.length}] vectors in [${dur.toMillis}] ms")
                          else ZIO.succeed(())
                        } yield n + vecs.length
                    }
                  _ <- execute(refreshIndex(index))
                  _ <- log.info(s"Merging index [$index] into 1 segment")
                  (dur, _) <- execute(forceMerge(index).maxSegments(1)).timed
                  _ <- log.info(s"Merged index [$index] in [${dur.toSeconds}] seconds")
                  _ <- execute(refreshIndex(index))
                } yield n
              }
            }

            def search(index: String,
                       queries: Stream[Throwable, NearestNeighborsQuery],
                       k: Int,
                       par: Int): ZStream[Logging with Clock, Throwable, QueryResult] = {
              queries.zipWithIndex.mapMPar(par) {
                case (query, i) =>
                  for {
                    (dur, res) <- ZIO.fromFuture(_ => client.nearestNeighbors(index, query, k, "id")).timed
                    _ <- if (i % 100 == 0) log.debug(s"Completed query [$i] in [$index] in [${dur.toMillis}] ms") else ZIO.succeed(())
                  } yield QueryResult(res.result.hits.hits.map(_.id), res.result.took)
              }
            }

            def deleteIndex(index: String): Task[Unit] = execute(ElasticDsl.deleteIndex(index)).map(_ => ())

            def close(): Task[Unit] = Task.succeed(client.close())

          }
        }
      }
    }

  }

  private class InMemoryCodec extends Lucene84Codec {
    override def getDocValuesFormatForField(field: String): DocValuesFormat = new DirectDocValuesFormat
    override def getPostingsFormatForField(field: String): PostingsFormat = new DirectPostingsFormat()
  }

//  def luceneInMemory(): ZLayer[Any, Nothing, Has[SearchClient]] = {
//    ZLayer.succeed {
//      new SearchClient {
//
//        private val searchers: mutable.Map[String, (Mapping, IndexSearcher)] = mutable.Map.empty
//        private val tmpDirs: mutable.Set[File] = mutable.Set.empty
//
//        def blockUntilReady(): Task[Unit] = Task.succeed(())
//        def indexExists(index: String): Task[Boolean] = ZIO.succeed(searchers.contains(index))
//        def buildIndex(index: String, mapping: Mapping, shards: Int, vectors: Stream[Throwable, Vec]) = {
//          val tmpDir = Files.createTempDirectory("elastiknn-").toFile
//          val indexDir = new MMapDirectory(tmpDir.toPath)
//          val indexWriterCfg = new IndexWriterConfig(new KeywordAnalyzer()).setCodec(new InMemoryCodec)
//          val indexWriter = new IndexWriter(indexDir, indexWriterCfg)
//          for {
//            n <- vectors.zipWithIndex.foldM(0L) {
//              case (n, (v, i)) =>
//                val t = v match {
//                  case sbv: Vec.SparseBool => VectorMapper.sparseBoolVector.checkAndCreateFields(mapping, "vec", sbv)
//                  case dfv: Vec.DenseFloat => VectorMapper.denseFloatVector.checkAndCreateFields(mapping, "vec", dfv)
//                  case _                   => Failure(new IllegalArgumentException("Vector must be either sparse bool or dense float"))
//                }
//                for {
//                  fields <- Task.fromTry(t)
//                  (dur, _) <- Task {
//                    val doc = new Document()
//                    doc.add(new StoredField("id", s"v$i"))
//                    fields.foreach(doc.add)
//                    indexWriter.addDocument(doc)
//                  }.timed
//                  _ <- if (i % 10000 == 0) log.debug(s"Indexed [$i] in [${dur.toMillis}] ms") else ZIO.succeed(())
//                } yield n + 1
//            }
//          } yield {
//            indexWriter.commit()
//            indexWriter.forceMerge(shards)
//            indexWriter.close()
//            searchers.put(index, (mapping, new IndexSearcher(DirectoryReader.open(indexDir))))
//            tmpDirs.add(tmpDir)
//            n
//          }
//        }
//
//        def search(index: String, queries: Stream[Throwable, NearestNeighborsQuery], k: Int, par: Int) =
//          searchers.get(index) match {
//            case None => Stream.fail(new NoSuchElementException(s"Index [$index] does not exist"))
//            case Some((mapping, searcher)) =>
//              queries.zipWithIndex.mapMPar(par) {
//                case (query, i) =>
//                  for {
//                    (dur, ids) <- Task.succeed {
//                      val luceneQuery = KnnQueryBuilder(query, mapping, searcher.getIndexReader)
//                      val hits = searcher.search(luceneQuery, k)
//                      hits.scoreDocs.map(d => searcher.doc(d.doc, Set("id").asJava).get("id"))
//                    }.timed
//                    _ <- if (i % 100 == 0) log.debug(s"Completed query [$i] in [${dur.toMillis}] ms") else ZIO.succeed(())
//                  } yield QueryResult(ids, dur.toMillis)
//              }
//          }
//
//        def deleteIndex(index: String): Task[Unit] = {
//          Task.succeed {
//            synchronized {
//              searchers.remove(index)
//            }
//          }
//        }
//
//        def close(): Task[Unit] = Task.succeed(())
//      }
//    }
//  }

}
