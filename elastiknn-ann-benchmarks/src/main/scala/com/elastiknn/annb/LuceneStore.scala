package com.elastiknn.annb

import akka.Done
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.commons.io.FileUtils
import org.apache.commons.math3.stat.StatUtils
import org.apache.lucene.index._
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.MMapDirectory

import java.lang
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
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

  final case class FrequencySummary(
      mean: Float,
      variance: Float,
      min: Long,
      p50: Long,
      p90: Long,
      p99: Long,
      max: Long
  )

  final case class FieldMetrics(
      /** Name of the field. */
      fieldName: String,
      /** Total number of documents with a term for this field. */
      numDocs: Long,
      /** Total number of terms stored for this field, across all segments. */
      numTermsStored: Long,
      /** Total number of _unique_ terms stored for this field, across all segments. */
      numDistinctTerms: Long,
      /**
        * Terms stored divided by distinct terms.
        * The higher the value, the more segments need to be visited to match this term.
        * A value of 1 means each term occurs in only one segment.
        * A much higher value might indicate the terms should be partitioned more carefully among segments.
        */
      termRedundancy: Float,
      /**
        * Summary statistics for the documents per term.
        * A large spread and/or variance indicates there are some very frequent terms and some very in-frequent terms.
        */
      numDocsPerTerm: FrequencySummary
  )

  final case class IndexMetrics(numSegments: Int, numDocs: Long, sizeOnDiskGb: Float, fieldStatistics: Seq[FieldMetrics]) {
    import io.circe.generic.auto._
    import io.circe.syntax._
    def prettySortedJson: String = this.asJson.spaces2
  }

  object IndexMetrics {
    def apply(indexDirectoryPath: Path): IndexMetrics = {
      val mmapDirectory = new MMapDirectory(indexDirectoryPath)
      val indexReader = DirectoryReader.open(mmapDirectory)
      try {
        val indexSearcher = new IndexSearcher(indexReader)
        val numSegments = indexReader.leaves().size()
        val leaves = indexReader.leaves().iterator().asScala.toList
        val fields = leaves.flatMap(_.reader().getFieldInfos.asScala.map(_.name)).distinct.sorted
        val fieldStatistics = for {
          field <- fields
          collectionStats = indexSearcher.collectionStatistics(field)
          if collectionStats != null
        } yield {
          val numTermsStored = leaves.map(_.reader().terms(field).size()).sum
          val termToFrequency: Map[String, Double] = leaves
            .map(_.reader())
            .flatMap { r =>
              val terms = r.terms(field)
              val termsEnum = terms.iterator()
              // Actually have to use string here to get accurate metrics.
              // Other unique identifiers, e.g., termsEnum.term().hashCode, seem to produce collisions.
              val arrayBuffer = new ArrayBuffer[(String, Int)](terms.size().toInt)
              while (termsEnum.next() != null) {
                arrayBuffer.append((termsEnum.term().utf8ToString(), termsEnum.docFreq()))
              }
              arrayBuffer.toList
            }
            .groupBy(_._1)
            .mapValues(_.map(_._2).sum.toDouble)
          val numDistinctTerms = termToFrequency.size
          val docsPerTerm = termToFrequency.values.toArray
          FieldMetrics(
            fieldName = field,
            numTermsStored = numTermsStored,
            numDistinctTerms = numDistinctTerms,
            termRedundancy = numTermsStored * 1f / numDistinctTerms,
            numDocs = collectionStats.docCount(),
            numDocsPerTerm = FrequencySummary(
              StatUtils.mean(docsPerTerm).toFloat,
              StatUtils.variance(docsPerTerm).toFloat,
              docsPerTerm.min.toLong,
              StatUtils.percentile(docsPerTerm, 0.5).toLong,
              StatUtils.percentile(docsPerTerm, 0.9).toLong,
              StatUtils.percentile(docsPerTerm, 0.99).toLong,
              docsPerTerm.max.toLong
            )
          )
        }
        val sizeOnDiskGb = FileUtils.sizeOfDirectory(indexDirectoryPath.toFile) / 1e9.toFloat
        IndexMetrics(
          numSegments,
          indexReader.numDocs(),
          sizeOnDiskGb,
          fieldStatistics
        )
      } finally {
        indexReader.close()
        mmapDirectory.close()
      }
    }
  }

  final class Default(val indexPath: Path) extends LuceneStore {
    override def index(parallelism: Int): Sink[lang.Iterable[IndexableField], Future[Done]] =
      Flow
        .fromMaterializer {
          case (mat, _) =>
            import mat.{system => sys}
            val runtime = Runtime.getRuntime
            val ramPerThreadLimitMB: Int = 2047.min((runtime.maxMemory() / 1e6).toInt / parallelism)
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

              val indexStatistics = IndexMetrics(indexPath)
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
