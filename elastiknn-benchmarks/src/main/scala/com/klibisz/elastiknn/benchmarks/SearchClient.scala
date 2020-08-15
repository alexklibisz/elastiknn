package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit

import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Vec}
import com.klibisz.elastiknn.client.{ElastiknnClient, ElastiknnRequests}
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.query.KnnQueryBuilder
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.HealthStatus
import com.sksamuel.elastic4s.{ElasticDsl, Handler, Response}
import org.apache.lucene.codecs.{DocValuesFormat, PostingsFormat}
import org.apache.lucene.codecs.lucene84.Lucene84Codec
import org.apache.lucene.codecs.memory.{DirectDocValuesFormat, DirectPostingsFormat}
import org.apache.lucene.index.{DirectoryReader, IndexReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.document.{Document, Field, StoredField}
import org.apache.lucene.search.IndexSearcher
import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.logging._
import zio.stream._

import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Failure

trait SearchClient {
  def blockUntilReady(): Task[Unit]
  def indexExists(index: String): Task[Boolean]
  def buildIndex(index: String, mapping: Mapping, shards: Int, vectors: Stream[Throwable, Vec]): Task[Long]
  def search(index: String, queries: Stream[Throwable, NearestNeighborsQuery], k: Int, parallelism: Int): Stream[Throwable, QueryResult]
  def close(): Task[Unit]
}

object SearchClient {

  def elasticsearch(uri: URI,
                    strictFailure: Boolean,
                    timeoutMillis: Int): ZLayer[Has[Logger[String]] with Has[Clock.Service], Throwable, Has[SearchClient]] = {

    ZLayer.fromServicesM[Logger[String], Clock.Service, Any, Throwable, SearchClient] {
      case (log, clock) =>
        ZIO.fromFuture { implicit ec =>
          Future {
            new SearchClient {

              private val clockLayer = ZLayer.succeed(clock)

              private val client = ElastiknnClient.futureClient(uri.getHost, uri.getPort, strictFailure, timeoutMillis)

              private def execute[T, U](t: T)(implicit handler: Handler[T, U], manifest: Manifest[U]): Task[Response[U]] =
                Task.fromFuture(_ => client.execute(t))

              def blockUntilReady(): Task[Unit] = {
                val check = clusterHealth.waitForStatus(HealthStatus.Yellow).timeout("90s")
                val sched = Schedule.recurs(10) && Schedule.spaced(Duration(10, TimeUnit.SECONDS))
                execute(check).retry(sched).map(_ => ()).provideLayer(clockLayer)
              }

              def indexExists(index: String): Task[Boolean] =
                execute(ElasticDsl.indexExists(index)).map(_.result.exists).catchSome {
                  case _: ElastiknnClient.StrictFailureException => ZIO.succeed(false)
                }

              def buildIndex(index: String, mapping: Mapping, shards: Int, vectors: Stream[Throwable, Vec]): Task[Long] =
                for {
                  _ <- log.info(s"Creating index [$index] with [0] replicas and [$shards] shards")
                  _ <- indexExists(index)
                  _ <- execute(createIndex(index).replicas(0).shards(shards).indexSetting("refresh_interval", "-1"))
                  _ <- execute(ElastiknnRequests.putMapping(index, "vec", "id", mapping))
                  n <- vectors
                    .grouped(200)
                    .zipWithIndex
                    .foldM(0L) {
                      case (n, (vecs, batchIndex)) =>
                        val ids = vecs.indices.map(i => s"$batchIndex-$i")
                        for {
                          (dur, _) <- Task.fromFuture(_ => client.index(index, "vec", vecs, "id", ids)).timed.provideLayer(clockLayer)
                          _ <- log.info(s"Indexed batch [$batchIndex] with [${vecs.length}] vectors in [${dur.toMillis}] ms")
                        } yield n + vecs.length
                    }
                  _ <- execute(refreshIndex(index))
                  _ <- execute(forceMerge(index).maxSegments(1))
                  _ <- execute(refreshIndex(index))
                } yield n

              def search(index: String,
                         queries: Stream[Throwable, NearestNeighborsQuery],
                         k: Int,
                         parallelism: Int): Stream[Throwable, QueryResult] =
                queries.zipWithIndex.mapMPar(parallelism) {
                  case (query, i) =>
                    for {
                      res <- execute(ElastiknnRequests.nearestNeighbors(index, query, k, "id"))
                      _ <- if (i % 100 == 0) log.debug(s"Completed query [$i] in [$index] in [${res.result.took}] ms") else ZIO.succeed(())
                    } yield QueryResult(res.result.hits.hits.map(_.id), res.result.took)
                }

              def close(): Task[Unit] = Task.succeed(client.close())
            }
          }
        }
    }

  }

  def luceneInMemory(): Layer[Throwable, Has[SearchClient]] =
    ZLayer.succeed {

      class InMemoryCodec extends Lucene84Codec {
        override def getDocValuesFormatForField(field: String): DocValuesFormat = new DirectDocValuesFormat
        override def getPostingsFormatForField(field: String): PostingsFormat = new DirectPostingsFormat()
      }

      new SearchClient {

        private val indices: mutable.Map[String, (Mapping, IndexReader)] = mutable.Map.empty
        private val tmpDirs: mutable.Set[File] = mutable.Set.empty

        def blockUntilReady(): Task[Unit] = Task.succeed(())
        def indexExists(index: String): Task[Boolean] = ZIO.succeed(indices.contains(index))
        def buildIndex(index: String, mapping: Mapping, shards: Int, vectors: Stream[Throwable, Vec]): Task[Long] = {
          val tmpDir = Files.createTempDirectory("elastiknn-").toFile
          val indexDir = new MMapDirectory(tmpDir.toPath)
          val indexWriterCfg = new IndexWriterConfig(new KeywordAnalyzer()).setCodec(new InMemoryCodec)
          val indexWriter = new IndexWriter(indexDir, indexWriterCfg)
          for {
            n <- vectors.zipWithIndex.foldM(0L) {
              case (n, (v, i)) =>
                val t = v match {
                  case sbv: Vec.SparseBool => VectorMapper.sparseBoolVector.checkAndCreateFields(mapping, "vec", sbv)
                  case dfv: Vec.DenseFloat => VectorMapper.denseFloatVector.checkAndCreateFields(mapping, "vec", dfv)
                  case _                   => Failure(new IllegalArgumentException("Vector must be either sparse bool or dense float"))
                }
                for {
                  fields <- Task.fromTry(t)
                  doc = new Document()
                  _ = doc.add(new StoredField("id", s"v$i"))
                  _ = fields.foreach(doc.add)
                  _ = indexWriter.addDocument(doc)
                } yield n + 1
            }
          } yield {
            indexWriter.commit()
            indexWriter.forceMerge(shards)
            indexWriter.close()
            indices.put(index, (mapping, DirectoryReader.open(indexDir)))
            tmpDirs.add(tmpDir)
            n
          }
        }
        def search(index: String,
                   queries: Stream[Throwable, NearestNeighborsQuery],
                   k: Int,
                   parallelism: Int): Stream[Throwable, QueryResult] =
          indices.get(index) match {
            case None => Stream.fail(new NoSuchElementException(s"Index [$index] does not exist"))
            case Some((mapping, indexReader)) =>
              val searcher = new IndexSearcher(indexReader)
              queries.mapMPar(parallelism) { query =>
                Task {
                  val luceneQuery = KnnQueryBuilder(query, mapping, indexReader)
                  val t0 = System.currentTimeMillis()
                  val topDocs = searcher.search(luceneQuery, k)
                  val t1 = System.currentTimeMillis()
                  val ids = topDocs.scoreDocs.map { d =>
                    searcher.doc(d.doc, Set("id").asJava).get("id")
                  }
                  QueryResult(ids, t1 - t0)
                }
              }
          }

        def close(): Task[Unit] = Task.succeed(())
      }
    }

}
