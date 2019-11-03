package com.klibisz.elastiknn

import java.util

import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}
import org.elasticsearch.ingest.ConfigurationUtils._

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
      val fieldRaw = readStringProperty(TYPE, tag, config, "fieldRaw")
      val fieldPrc = readStringProperty(TYPE, tag, config, "fieldProcessed")
      val dim = readIntProperty(TYPE, tag, config, "dimension", 0)
      val distStr = readStringProperty(TYPE, tag, config, "distance")
      lazy val distErrStr =
        s"Failed to parse ${distStr}. Should be one of ${Distance.values.map(_.name).mkString(",")}"
      val distEnum: Distance = Distance
        .fromName(distStr)
        .getOrElse(
          throw newConfigurationException(TYPE, tag, "distance", distErrStr))

      val model = if (config.containsKey("lsh")) {
        val lsh = config.get("lsh").asInstanceOf[util.Map[String, Object]]
        val k = readIntProperty(TYPE, tag, lsh, "k", 0)
        val L = readIntProperty(TYPE, tag, lsh, "L", 0)
        if (!(k > 0 && L > 0))
          throw newConfigurationException(TYPE,
                                          tag,
                                          "lsh",
                                          "k and L must both be > 0")
        ProcessorOptions.Model.Lsh(LSHModel(k = k, l = L))
      } else {
        ProcessorOptions.Model.Exact(ExactModel.defaultInstance)
      }

      val processorOptions =
        ProcessorOptions(fieldRaw, fieldPrc, dim, distEnum, model)

      new ElastiKnnProcessor(tag, processorOptions)
    }

  }

}
