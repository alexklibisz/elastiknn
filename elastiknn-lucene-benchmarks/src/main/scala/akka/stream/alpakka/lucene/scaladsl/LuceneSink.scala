package akka.stream.alpakka.lucene.scaladsl

import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import org.apache.lucene.document.Document
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig}
import org.apache.lucene.store.Directory

import scala.concurrent.{ExecutionContext, Future}

object LuceneSink {

  def create(indexDirectory: Directory, indexWriterConfig: IndexWriterConfig, parallelism: Int)(
      implicit ec: ExecutionContext
  ): Sink[Document, Future[Done]] =
    create(new IndexWriter(indexDirectory, indexWriterConfig), parallelism)

  def create(indexWriter: IndexWriter, parallelism: Int)(implicit ec: ExecutionContext): Sink[Document, Future[Done]] =
    Flow[Document]
      .mapAsyncUnordered(parallelism) { doc => Future(indexWriter.addDocument(doc)) }
      .toMat(Sink.ignore) {
        case (_: NotUsed, f: Future[Done]) =>
          f.andThen {
            case _ => indexWriter.close()
          }
      }

}
