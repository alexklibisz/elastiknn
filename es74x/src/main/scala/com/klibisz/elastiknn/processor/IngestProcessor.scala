package com.klibisz.elastiknn.processor

import java.util

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.VectorType.VECTOR_TYPE_FLOAT
import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn._
import io.circe.syntax._
import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}
import scalapb_circe.JsonFormat

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class IngestProcessor private (tag: String, client: NodeClient, popts: ProcessorOptions) extends AbstractProcessor(tag) {

  import IngestProcessor.TYPE
  import popts._

  private def parseFloatVector(doc: IngestDocument): Try[FloatVector] = {
    val key = floatVectorPath(fieldRaw)
    Try(doc.getFieldValue(key, classOf[util.List[Double]]))
      .map(ld => FloatVector(values = ld.asScala.toArray))
      .recoverWith {
        case _ => Failure(ParseVectorException(Some(s"Failed to parse vector of doubles at $key")))
      }
      .flatMap { dv =>
        if (dv.values.length == popts.dimension) Success(dv)
        else Failure(VectorDimensionException(dv.values.length, popts.dimension))
      }
  }

  private def parseBoolVector(doc: IngestDocument): Try[BoolVector] = {
    val key = boolVectorPath(fieldRaw)
    Try(doc.getFieldValue(key, classOf[util.List[Boolean]]))
      .map(ld => BoolVector(values = ld.asScala.toArray))
      .recoverWith {
        case _ => Failure(ParseVectorException(Some(s"Failed to parse vector of booleans at $key")))
      }
      .flatMap { dv =>
        if (dv.values.length == popts.dimension) Success(dv)
        else Failure(VectorDimensionException(dv.values.length, popts.dimension))
      }
  }

  override def getType: String = IngestProcessor.TYPE

  /** This is the method that gets invoked when someone adds a document that uses an elastiknn pipeline. */
  override def execute(doc: IngestDocument): IngestDocument = {

    // Check if the raw vector is present.
    require(doc.hasField(fieldRaw), s"$TYPE expected to find vector at $fieldRaw")

    popts.modelOptions match {
      // For exact models, just make sure the vector can be parsed.
      case ModelOptions.Empty | ModelOptions.Exact(_) =>
        if (popts.vectorType == VECTOR_TYPE_FLOAT) parseFloatVector(doc).get else parseBoolVector(doc).get
    }

    doc

  }

}

object IngestProcessor {

  lazy val TYPE: String = ELASTIKNN_NAME

  class Factory(client: NodeClient) extends Processor.Factory {

    private val logger: Logger = LogManager.getLogger(getClass)

    /** This is the method that gets invoked when someone creates an elastiknn pipeline. */
    override def create(registry: util.Map[String, Processor.Factory], tag: String, config: util.Map[String, Object]): IngestProcessor = {
      val configJson = config.asJson
      val popts = JsonFormat.fromJson[ProcessorOptions](configJson)
      config.clear() // Need to do this otherwise es thinks parsing didn't work.
      new IngestProcessor(tag, client, popts)
    }

  }

}
