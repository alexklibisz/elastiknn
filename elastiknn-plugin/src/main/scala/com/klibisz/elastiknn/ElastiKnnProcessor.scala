package com.klibisz.elastiknn

import java.util

import com.klibisz.elastiknn.circe._
import io.circe.syntax._
import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}
import scalapb_circe.JsonFormat

class ElastiKnnProcessor private (tag: String,
                                  processorOptions: ProcessorOptions)
    extends AbstractProcessor(tag) {
  override def execute(ingestDocument: IngestDocument): IngestDocument = {
    ingestDocument
  }

  override def getType: String = {
    Constants.name
  }
}

object ElastiKnnProcessor {

  final val TYPE: String = Constants.name

  class Factory extends Processor.Factory {

    override def create(
        registry: util.Map[String, Processor.Factory],
        tag: String,
        config: util.Map[String, Object]): ElastiKnnProcessor = {
      val json = config.asJson
      val popts = JsonFormat.fromJson[ProcessorOptions](json)
      config.clear() // Need to do this otherwise es thinks parsing didn't work.
      new ElastiKnnProcessor(tag, popts)
    }

  }

}
