package akka.stream.alpakka.lucene.scaladsl

import akka.dispatch.ExecutionContexts
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import org.apache.lucene.document.Document
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig}
import org.apache.lucene.store.Directory

import scala.concurrent.Future

object LuceneSink {

  def create(indexDirectory: Directory, indexWriterConfig: IndexWriterConfig): Sink[Document, Future[Done]] =
    create(new IndexWriter(indexDirectory, indexWriterConfig))

  def create(indexWriter: IndexWriter): Sink[Document, Future[Done]] =
    Flow[Document]
      .map(indexWriter.addDocument(_))
      .toMat(Sink.ignore) {
        case (_: NotUsed, f: Future[Done]) =>
          f.andThen {
            case _ => indexWriter.close()
          }(ExecutionContexts.parasitic)
      }

}
