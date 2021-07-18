package com.klibisz.ann1b

import akka.Done
import akka.stream.scaladsl._
import com.klibisz.elastiknn.models.HashingModel
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.FileUtils
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.store.MMapDirectory

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait LuceneModel {

  def index(indexPath: Path, parallelism: Int)(implicit ec: ExecutionContext): Sink[Dataset.Doc, Future[Done]]

  def search(): Unit

}

final class ElastiknnLshLuceneModel(val hashingModel: HashingModel.DenseFloat) extends LuceneModel with StrictLogging {

  private val rt = Runtime.getRuntime
  private val idFieldName: String = "i"
  private val vecFieldName: String = "v"

  override def index(indexPath: Path, parallelism: Int)(
      implicit ec: ExecutionContext
  ): Sink[Dataset.Doc, Future[Done]] = {

    val ramPerThreadLimitMB: Int = 2047.min((rt.maxMemory() / 1e6).toInt / rt.availableProcessors())

    val mmd = new MMapDirectory(indexPath)
    val ixc = new IndexWriterConfig()
      .setMaxBufferedDocs(Int.MaxValue)
      .setRAMBufferSizeMB(Double.MaxValue)
      .setRAMPerThreadHardLimitMB(ramPerThreadLimitMB)
      .setMergePolicy(NoMergePolicy.INSTANCE)
    val ixw = new IndexWriter(mmd, ixc)

    val idFt = new FieldType()
    idFt.setStored(true)

    val vecFt = new FieldType()
    vecFt.setStored(false)
    vecFt.setOmitNorms(true)
    vecFt.setIndexOptions(IndexOptions.DOCS)
    vecFt.setTokenized(false)
    vecFt.setStoreTermVectors(false)

    def addDocument(d: Dataset.Doc): Future[(Dataset.Doc, Long)] = Future {
      val hashes = hashingModel.hash(d.vec)
      val fields = new java.util.ArrayList[Field](hashes.length + 1)
      fields.add(new Field(idFieldName, d.id, idFt))
      hashes.foreach { hf => fields.add(new Field(vecFieldName, hf.hash, vecFt)) }
      val i = ixw.addDocument(fields)
      d -> i
    }

    val streamStartedAtNanos = new AtomicLong()

    val logStartMessage = () => {
      streamStartedAtNanos.set(System.nanoTime())
      logger.info(s"Indexing to $mmd with RAM limit/thread = ${ramPerThreadLimitMB}mb")
    }

    val closeAndLogInfo = () => {
      val streamDuration = (System.nanoTime() - streamStartedAtNanos.get()).nanos
      val closeStartedAtNanos = System.nanoTime()
      logger.info(s"Closing $mmd")
      ixw.close()
      val closeDuration = (System.nanoTime() - closeStartedAtNanos).nanos
      logger.info(s"Closed $mmd")
      val rdr = DirectoryReader.open(mmd)
      val leavesCount = rdr.leaves().size()
      rdr.close()
      val size = FileUtils.sizeOfDirectory(indexPath.toFile) / 1e9
      logger.info(s"""Indexing summary:
          |  directory              ${indexPath.toFile}
          |  duration (in stream)   ${streamDuration.toSeconds} seconds
          |  duration (closing)     ${closeDuration.toSeconds} seconds
          |  segments               $leavesCount
          |  size                   ${size.formatted("%.2f")} GB""".stripMargin)
    }

    Flow[Dataset.Doc]
      .mapAsyncUnordered(parallelism)(addDocument)
      .prependLazy(Source.lazySingle(logStartMessage))
      .concatLazy(Source.lazySingle(closeAndLogInfo))
      .toMat(Sink.ignore)(Keep.right)
  }

  override def search(): Unit = ???
}
