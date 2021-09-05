package com.elastiknn.annb

import akka.Done
import akka.stream.scaladsl.Sink
import org.apache.lucene.index.IndexableField

import java.nio.file.Path
import scala.concurrent.Future

/**
  * Various Sinks for a stream of documents.
  */
object LuceneSink {

  def store(indexPath: Path, parallelism: Int): Sink[java.lang.Iterable[IndexableField], Future[Done]] = ???

}
