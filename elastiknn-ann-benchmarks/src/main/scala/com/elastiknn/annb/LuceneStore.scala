package com.elastiknn.annb

import akka.Done
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.lucene.index._
import org.apache.lucene.store.MMapDirectory

import java.lang
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Future
import scala.concurrent.duration._

trait LuceneStore {
  def indexPath: Path
  def index(parallelism: Int): Sink[java.lang.Iterable[IndexableField], Future[Done]]
  def reader(): DirectoryReader
}

/**
  * Various Sinks for a stream of documents.
  */
object LuceneStore {

  final class Default(val indexPath: Path) extends LuceneStore {
    override def index(parallelism: Int): Sink[lang.Iterable[IndexableField], Future[Done]] =
      Flow
        .fromMaterializer {
          case (mat, _) =>
            import mat.{system => sys}
            val ramPerThreadLimitMB: Int = 2047.min((Runtime.getRuntime.maxMemory() / 1e6).toInt / parallelism)
            val ixc = new IndexWriterConfig()
              .setMaxBufferedDocs(Int.MaxValue)
              .setRAMBufferSizeMB(Double.MaxValue)
              .setRAMPerThreadHardLimitMB(ramPerThreadLimitMB)
              .setMergePolicy(NoMergePolicy.INSTANCE)
            val mmd = new MMapDirectory(indexPath)
            val ixw = new IndexWriter(mmd, ixc)
            val streamStartedNanos = new AtomicLong()
            val logStartMessage = () => {
              streamStartedNanos.set(System.nanoTime())
              sys.log.info(s"Indexing to directory [$mmd] with RAM limit [${ramPerThreadLimitMB}mb/thread]")
            }
            val closeAndLogStatistics = () => {
              val streamDuration = (System.nanoTime() - streamStartedNanos.get()).nanos
              val closeStartedNanos = System.nanoTime()
              sys.log.info(s"Closing directory [$mmd]")
              ixw.close()
              val closeDuration = (System.nanoTime() - closeStartedNanos).nanos
              sys.log.info(s"Closed directory [$mmd]")

              // Collect some statistics about the index and terms.
              sys.log.info(
                "Indexing complete: " + List(
                  s"directory [${indexPath.toFile}]",
                  s"time in stream [${streamDuration.toSeconds}s]",
                  s"time in closing [${closeDuration.toSeconds}s]"
                ).mkString(", ")
              )

              val indexStatistics = LuceneIndexMetrics(indexPath)
              sys.log.info(indexStatistics.prettySortedJson)
            }

            Flow[java.lang.Iterable[IndexableField]]
              .mapAsyncUnordered(parallelism)(fields => Future(ixw.addDocument(fields))(mat.executionContext))
              .prependLazy(Source.lazySingle(logStartMessage))
              .concatLazy(Source.lazySingle(closeAndLogStatistics))
        }
        .toMat(Sink.ignore)(Keep.right)

    override def reader(): DirectoryReader = DirectoryReader.open(new MMapDirectory(indexPath))
  }
}
