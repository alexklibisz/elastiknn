package com.klibisz.elastiknn

import java.util

import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn.utils.LRUCache
import com.klibisz.elastiknn.utils.ProtobufUtils._
import io.circe.syntax._
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}
import scalapb_circe.JsonFormat

class ElastiKnnProcessor private (tag: String, nodeClient: NodeClient, popts: ProcessorOptions, model: ElastiKnnModel)
    extends AbstractProcessor(tag) {

  import ElastiKnnProcessor.TYPE
  import popts._

  /** This is the method that gets invoked when someone adds a document that uses an elastiknn pipeline. */
  override def execute(doc: IngestDocument): IngestDocument = {

    // Check if the raw vector is present.
    require(doc.hasField(fieldRaw), s"$TYPE expected to find vector at $fieldRaw")

    // Parse vector into a string.
    val vecRaw = doc.getFieldValue(fieldRaw, classOf[String])
    val vecProcessed = model.process(vecRaw).get

    // Insert it to the document.
    doc.setFieldValue(fieldProcessed, vecProcessed.asMessage.asJavaMap)
    doc
  }

  override def getType: String = ElastiKnnProcessor.TYPE
}

object ElastiKnnProcessor {

  lazy val TYPE: String = "elastiknn"

  private val modelCache = new LRUCache[ProcessorOptions, ElastiKnnModel](100)

  class Factory(nodeClient: NodeClient) extends Processor.Factory {

    /** This is the method that gets invoked when someone creates an elastiknn pipeline. */
    override def create(registry: util.Map[String, Processor.Factory],
                        tag: String,
                        config: util.Map[String, Object]): ElastiKnnProcessor = {
      val json = config.asJson
      val popts = JsonFormat.fromJson[ProcessorOptions](json)
      lazy val err = s"Failed to instantiate model from given configuration: $config"
      val model = modelCache.get(popts, _ => ElastiKnnModel(popts).getOrElse(throw new IllegalArgumentException(err)))
      config.clear() // Need to do this otherwise es thinks parsing didn't work.
      new ElastiKnnProcessor(tag, nodeClient, popts, model)
    }

  }

}
