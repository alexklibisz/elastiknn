package com.elastiknn.annb

import akka.Done
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.commons.io.FileUtils
import org.apache.lucene.index._
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.util.BytesRef

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

  private def commas(l: Long): String = l.toString.reverse.grouped(3).mkString(",").reverse

  final case class FieldMetrics(
      fieldName: String,
      numTerms: Long,
      numDistinctTerms: Long
  ) {
    override def toString: String =
      s"FieldMetrics(fieldName=$fieldName, numTerms=${commas(numTerms)}, numDistinctTerms=${commas(numDistinctTerms)})"
  }

  final case class IndexMetrics(numSegments: Int, numDocs: Long, sizeOnDiskGb: Double, fieldStatistics: Seq[FieldMetrics]) {
    override def toString: String =
      s"""IndexMetrics(
         |  numSegments=$numSegments, 
         |  numDocs=${commas(numDocs)},
         |  sizeOnDiskGb=${sizeOnDiskGb.toFloat},
         |  fieldStatistics=
         |    ${fieldStatistics.map(_.toString).mkString(",\n")}
         |)""".stripMargin
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
          val numTerms = leaves.map(_.reader().terms(field).size()).sum
          val numDistinctTerms = leaves
            .map(_.reader())
            .flatMap { r =>
              val terms = r.terms(field)
              val termsEnum = terms.iterator()
              // Actually have to use string here to get accurate metrics.
              // Other unique identifiers, e.g., hashcode, seem to produce collisions.
              val arrayBuffer = new ArrayBuffer[String](terms.size().toInt)
              while (termsEnum.next() != null) {
                arrayBuffer.append(termsEnum.term().utf8ToString())
              }
              arrayBuffer.toList
            }
            .distinct
            .length
          FieldMetrics(
            field,
            numTerms,
            numDistinctTerms
          )
        }
        val sizeOnDiskGb = FileUtils.sizeOfDirectory(indexDirectoryPath.toFile) / 1e9
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
              sys.log.info(indexStatistics.toString)

//              val fieldNames: Seq[String] = leaves.flatMap(_.reader().getFieldInfos.asScala.map(_.name)).distinct
//
////              indexSearcher.collectionStatistics()
//
//              // Field stats for [v]: field="v",maxDoc=100000,docCount=100000,sumTotalTermFreq=1000000,sumDocFreq=1000000
//              for {
//                field <- fieldNames
//                collectionStats = indexSearcher.collectionStatistics(field)
//                if collectionStats != null
//              } {
//                sys.log.info(
//                  s"Field stats for [${field}]: " + List(
////                    s"${collectionStats.sumTotalTermFreq()}",
//                    collectionStats.toString
//                  ).mkString(", ")
//                )
//              }
//
//              // (field -> (term hash code, docs w/ term))
//              val termCountsByField = for {
//                field <- fieldNames.filterNot(_ == "id")
//              } yield (field, for {
//                leafReader <- leaves.map(_.reader())
//                terms: Terms = leafReader.terms(field)
////                termsEnum: TermsEnum = terms.iterator()
////                _ = termsEnum match {
////                  case s: org.apache.lucene.codecs.blocktree.SegmentTermsEnum => s.computeBlockStats()
////                }
////                _ = println(termsEnum)
////                _ <- 0L until terms.size()
////                _ = termsEnum.next()
////                t = termsEnum.term()
//              } yield terms.size().max(0))
//
//              // Term stats: field [v], termCounts [721621]
//              // Term stats: field [v], termCounts [775269]
//
//              for {
//                (field, termCounts) <- termCountsByField
//              } {
//                sys.log.info(
//                  s"Term stats: " + List(
//                    s"field [$field]",
//                    s"number of terms for this field = [${termCounts.sum}]"
//                  ).mkString(", ")
//                )
//              }
//              indexReader.close()
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
