package com.klibisz.ann1b

import akka.stream.Attributes
import akka.{Done, NotUsed}
import akka.stream.scaladsl._
import com.klibisz.elastiknn.models.HashingModel
import com.typesafe.scalalogging.StrictLogging
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index.{IndexOptions, IndexWriter, IndexWriterConfig, NoMergePolicy}
import org.apache.lucene.store.MMapDirectory

import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future, Promise}

trait LuceneModel {

  def index(
      indexPath: Path,
      parallelism: Int
  )(implicit ec: ExecutionContext): Sink[Dataset.Doc, Future[Done]]

  def search(): Unit

}

object LuceneModel {

  final class ElastiknnLsh(val hashingModel: HashingModel.DenseFloat) extends LuceneModel with StrictLogging {

    private val rt = Runtime.getRuntime
    private val idFieldName: String = "i"
    private val vecFieldName: String = "v"

    override def index(indexPath: Path, parallelism: Int)(implicit ec: ExecutionContext): Sink[Dataset.Doc, Future[Done]] = {

      val ramPerThreadLimitMB: Int = 2047.min((rt.maxMemory() / 1e6 * 0.6).toInt / rt.availableProcessors())

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

      def addDocument(d: Dataset.Doc) = Future {
        val hashes = hashingModel.hash(d.vec)
        val fields = new java.util.ArrayList[Field](hashes.length + 1)
        fields.add(new Field(idFieldName, d.id, idFt))
        hashes.foreach { hf => fields.add(new Field(vecFieldName, hf.hash, vecFt)) }
        val i = ixw.addDocument(fields)
        if (i == 1) logger.info(s"Indexing to $mmd with RAM limit/thread = ${ramPerThreadLimitMB}mb")
        d -> i
      }

      Flow[Dataset.Doc]
        .mapAsyncUnordered(parallelism)(addDocument)
        .toMat(Sink.ignore) {
          case (_, f: Future[Done]) =>
            for {
              x <- f
              _ = logger.info(s"Flushing and closing to $mmd")
              _ <- Future(ixw.close())
              _ = logger.info(s"Closed $mmd")
            } yield x
        }
    }

    override def search(): Unit = ???
  }

}
