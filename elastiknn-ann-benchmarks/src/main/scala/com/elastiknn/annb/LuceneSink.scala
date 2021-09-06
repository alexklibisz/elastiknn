package com.elastiknn.annb

import akka.Done
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.commons.io.FileUtils
import org.apache.lucene.index._
import org.apache.lucene.store.MMapDirectory

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Various Sinks for a stream of documents.
  */
object LuceneSink {

  def store(indexPath: Path, parallelism: Int): Sink[java.lang.Iterable[IndexableField], Future[Done]] =
    Flow
      .fromMaterializer {
        case (mat, _) =>
          import mat.{system => sys}
          val runtime = Runtime.getRuntime
          val ramPerThreadLimitMB: Int = 2047.min((runtime.maxMemory() / 1e6).toInt / runtime.availableProcessors())
          val mmd = new MMapDirectory(indexPath)
          val ixc = new IndexWriterConfig()
            .setMaxBufferedDocs(Int.MaxValue)
            .setRAMBufferSizeMB(Double.MaxValue)
            .setRAMPerThreadHardLimitMB(ramPerThreadLimitMB)
            .setMergePolicy(NoMergePolicy.INSTANCE)
          val ixw = new IndexWriter(mmd, ixc)
          val streamStartedNanos = new AtomicLong()
          val logStartMessage = () => {
            streamStartedNanos.set(System.nanoTime())
            sys.log.info(s"Indexing to directory [$mmd] with RAM limit [${ramPerThreadLimitMB}mb/thread]")
          }
          val closeAndLogInfo = () => {
            val streamDuration = (System.nanoTime() - streamStartedNanos.get()).nanos
            val closeStartedNanos = System.nanoTime()
            sys.log.info(s"Closing directory [$mmd]")
            ixw.close()
            val closeDuration = (System.nanoTime() - closeStartedNanos).nanos
            sys.log.info(s"Closed directory [$mmd]")
            val rdr = DirectoryReader.open(mmd)
            val leavesCount = rdr.leaves().size()
            rdr.close()
            val size = FileUtils.sizeOfDirectory(indexPath.toFile) / 1e9
            sys.log.info(
              "Indexing complete: " + List(
                s"directory [${indexPath.toFile}]",
                s"time in stream [${streamDuration.toSeconds}s]",
                s"time in closing [${closeDuration.toSeconds}s]",
                s"size [${size.formatted("%.2f")}GB]"
              ).mkString(", ")
            )
          }

          Flow[java.lang.Iterable[IndexableField]]
            .mapAsyncUnordered(parallelism)(fields => Future(ixw.addDocument(fields))(mat.executionContext))
            .prependLazy(Source.lazySingle(logStartMessage))
            .concatLazy(Source.lazySingle(closeAndLogInfo))
      }
      .toMat(Sink.ignore)(Keep.right)

}
