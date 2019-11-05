package com.klibisz.elastiknn

import java.util

import io.circe.syntax._
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}
import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn.utils.ProtobufUtils._
import com.klibisz.elastiknn.utils.LRUCache
import scalapb_circe.JsonFormat

class ElastiKnnProcessor private (tag: String, nodeClient: NodeClient, popts: ProcessorOptions, model: ElastiKnnModel)
    extends AbstractProcessor(tag) {

  import popts._
  import ElastiKnnProcessor.TYPE

  /** This is the method that gets invoked when someone adds a document that uses an elastiknn pipeline. */
  override def execute(doc: IngestDocument): IngestDocument = {

    // Check if the raw vector is present.
    require(doc.hasField(fieldRaw), s"$TYPE expected to find vector at $fieldRaw")

    // Parse vector into a regular Java List.
    // TODO: Consider storing vectors as comma-separated strings and immediately parsing to primitive collection.
    val vecRawJava = doc.getFieldValue(fieldRaw, classOf[util.List[Double]])
    require(vecRawJava.size == dimension, s"$TYPE expected vector with $dimension elements but got ${vecRawJava.size}")

    doc.setFieldValue(fieldProcessed, popts.asJavaMap)

    doc
  }

  override def getType: String = ElastiKnnProcessor.TYPE
}

object ElastiKnnProcessor {

  lazy val TYPE: String = "elastiknn"

  private val modelCache = new LRUCache[ProcessorOptions, ElastiKnnModel](10)

  class Factory(nodeClient: NodeClient) extends Processor.Factory {

    /** This is the method that gets invoked when someone creates an elastiknn pipeline. */
    override def create(registry: util.Map[String, Processor.Factory],
                        tag: String,
                        config: util.Map[String, Object]): ElastiKnnProcessor = {
      val json = config.asJson
      val popts = JsonFormat.fromJson[ProcessorOptions](json)
      config.clear() // Need to do this otherwise es thinks parsing didn't work.
      val model = modelCache.get(popts, _ => ElastiKnnModel(popts))
      new ElastiKnnProcessor(tag, nodeClient, popts, model)
    }

  }

}
