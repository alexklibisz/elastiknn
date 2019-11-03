package com.klibisz.elastiknn

import java.util

import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}

class ElastiKnnProcessor private (tag: String) extends AbstractProcessor(tag) {
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
        processorTag: String,
        config: util.Map[String, Object]): ElastiKnnProcessor = {
      new ElastiKnnProcessor(processorTag)
    }

  }

}
