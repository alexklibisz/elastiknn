package com.elastiknn.annb

import akka.Done
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.commons.io.FileUtils
import org.apache.lucene.index._
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.MMapDirectory

import java.lang
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Future
import scala.concurrent.duration._
import collection.JavaConverters._

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
            val runtime = Runtime.getRuntime
            val ramPerThreadLimitMB: Int = 2047.min((runtime.maxMemory() / 1e6).toInt / runtime.availableProcessors())
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
              val indexReader = DirectoryReader.open(mmd)
              val indexSearcher = new IndexSearcher(indexReader)
              val leaves: Seq[LeafReaderContext] = indexReader.leaves().iterator().asScala.toList
              val size = FileUtils.sizeOfDirectory(indexPath.toFile) / 1e9
              sys.log.info(
                "Indexing complete: " + List(
                  s"directory [${indexPath.toFile}]",
                  s"time in stream [${streamDuration.toSeconds}s]",
                  s"time in closing [${closeDuration.toSeconds}s]",
                  s"segments [${leaves.length}]",
                  s"size [${size.formatted("%.2f")}GB]"
                ).mkString(", ")
              )
              val fieldNames: Seq[String] = leaves.flatMap(_.reader().getFieldInfos.asScala.map(_.name)).distinct

              // Field stats for [v]: field="v",maxDoc=100000,docCount=100000,sumTotalTermFreq=1000000,sumDocFreq=1000000

              for {
                field <- fieldNames
                collectionStats = indexSearcher.collectionStatistics(field)
                if collectionStats != null
              } {
                sys.log.info(
                  s"Field stats for [${field}]: " + List(
//                    s"${collectionStats.sumTotalTermFreq()}",
                    collectionStats.toString
                  ).mkString(", ")
                )
              }

//              // (field -> (term hash code, docs w/ term))
//              val termCountsByField = for {
//                field <- fieldNames.filterNot(_ == "id")
//              } yield (field, for {
//                leafReader <- leaves.map(_.reader())
//                terms = leafReader.terms(field)
//                termsEnum = terms.iterator()
//                _ = termsEnum.termState()
//                _ <- 0L until terms.size()
//                _ = termsEnum.next()
//                t = termsEnum.term()
//              } yield (t.bytes.slice(t.offset, t.length), termsEnum.docFreq()))
//
//              // Term stats: field [v], distinct terms [486], average docs per term [4897], average segments per term [1]
//              // Term stats: field [v], distinct terms [416], average docs per term [5777], average segments per term [1]
//              // Term stats: field [v], distinct terms [478], average docs per term [4974], average segments per term [1]
//
//              // Term stats: field [v], distinct terms [533], average docs per term [4486], average segments per term [1]
//              // Term stats: field [v], distinct terms [485], average docs per term [4920], average segments per term [1]
//
//              for {
//                (field, termCounts) <- termCountsByField
//              } {
//                val distinctTerms = termCounts.map(_._1).distinct
//                val avgDocsPerTerm = termCounts.map(_._2).sum / distinctTerms.length
//                val avgSegmentsPerTerm = termCounts.groupBy(_._1).map(_._2.length).sum / distinctTerms.length
//                sys.log.info(
//                  s"Term stats: " + List(
//                    s"field [$field]",
//                    s"distinct terms [${distinctTerms.length}]",
//                    s"average docs per term [$avgDocsPerTerm]",
//                    s"average segments per term [$avgSegmentsPerTerm]"
//                  ).mkString(", ")
//                )
//              }
              indexReader.close()
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
