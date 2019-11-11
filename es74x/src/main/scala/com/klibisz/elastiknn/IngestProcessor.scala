package com.klibisz.elastiknn

import java.util
import java.util.concurrent.Callable

import com.google.common.cache.{Cache, CacheBuilder, CacheLoader}
import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn.utils.LRUCache
import com.klibisz.elastiknn.utils.ProtobufUtils._
import io.circe.syntax._
import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptAction
import org.elasticsearch.action.admin.indices.mapping.put.{PutMappingAction, PutMappingRequestBuilder}
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}
import scalapb_circe.JsonFormat

import scala.collection.JavaConverters._
import scala.util.Try

class IngestProcessor private (tag: String, client: NodeClient, popts: ProcessorOptions, model: Model) extends AbstractProcessor(tag) {

  import IngestProcessor.TYPE
  import popts._

  /** This is the method that gets invoked when someone adds a document that uses an elastiknn pipeline. */
  override def execute(doc: IngestDocument): IngestDocument = {

    // Check if the raw vector is present.
    require(doc.hasField(fieldRaw), s"$TYPE expected to find vector at $fieldRaw")

    // Parse vector into a string.
    lazy val ex = new IllegalArgumentException(s"Failed parsing field $fieldRaw to a list of doubles")
    val vecRaw = Try(doc.getFieldValue(fieldRaw, classOf[util.List[Double]])).getOrElse(throw ex)
    val vecProcessed = model.process(vecRaw.asScala.toArray).get

    // Insert it to the document.
    doc.setFieldValue(fieldProcessed, vecProcessed.asJavaMap)

    // Optionally discard the original.
    if (discardRaw) doc.removeField(fieldRaw)

    // TODO: consider whether it should set/modify the mapping here. The tradeoff is that PUTing the mapping for every
    // new document will slow down ingestion.

    doc
  }

  override def getType: String = IngestProcessor.TYPE
}

object IngestProcessor {

  lazy val TYPE: String = ELASTIKNN_NAME

  private lazy val modelCache: Cache[ProcessorOptions, Model] =
    CacheBuilder.newBuilder.softValues.build[ProcessorOptions, Model]()

  class Factory(client: NodeClient) extends Processor.Factory {

    private val logger: Logger = LogManager.getLogger(getClass)

    private def createStoredScripts(): Unit =
      Seq(StoredScripts.exactAngular).foreach { s =>
        logger.info(s"Creating stored script ${s.id}")
        client.execute(PutStoredScriptAction.INSTANCE, s.putRequest)
      }

    /** This is the method that gets invoked when someone creates an elastiknn pipeline. */
    override def create(registry: util.Map[String, Processor.Factory], tag: String, config: util.Map[String, Object]): IngestProcessor = {
      val configJson = config.asJson
      val popts = JsonFormat.fromJson[ProcessorOptions](configJson)
      val call: Callable[Model] = () =>
        Model(popts).getOrElse(throw new IllegalArgumentException(s"Failed to instantiate model from given configuration: $config"))
      val model = modelCache.get(popts, call)
      config.clear() // Need to do this otherwise es thinks parsing didn't work.
//      createStoredScripts() // (Re-)create scripts.
      new IngestProcessor(tag, client, popts, model)
    }

  }

}
