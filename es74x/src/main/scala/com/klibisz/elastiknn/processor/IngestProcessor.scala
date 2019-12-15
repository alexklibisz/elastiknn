package com.klibisz.elastiknn.processor

import java.util

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.VectorType.VECTOR_TYPE_FLOAT
import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.utils.Implicits._
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

  private def parseVector(doc: IngestDocument): Try[ElastiKnnVector] =
    (for {
      srcMap <- Try(doc.getFieldValue(fieldRaw, classOf[util.Map[String, AnyRef]]))
      ekv <- ElastiKnnVector.from(srcMap)
    } yield ekv).recoverWith {
      case ex => Failure(ParseVectorException(Some(s"Failed to parse ${ElastiKnnVector.scalaDescriptor.name} from $fieldRaw: $ex")))
    }

  private def parseFloatVector(doc: IngestDocument): Try[FloatVector] =
    for {
      ekv <- parseVector(doc)
      ret <- ekv.vector match {
        case ElastiKnnVector.Vector.FloatVector(fv) =>
          if (fv.values.length == popts.dimension) Success(fv) else Failure(VectorDimensionException(fv.values.length, popts.dimension))
        case v => Failure(illArgEx(s"Expected vector of floats but got: $v"))
      }
    } yield ret

  private def parseBoolVector(doc: IngestDocument): Try[SparseBoolVector] =
    for {
      ekv <- parseVector(doc)
      ret <- ekv.vector match {
        case ElastiKnnVector.Vector.SparseBoolVector(sbv) =>
          if (sbv.totalIndices == popts.dimension) Success(sbv) else Failure(VectorDimensionException(sbv.totalIndices, popts.dimension))
        case v => Failure(illArgEx(s"Expected vector of booleans but got: $v"))
      }
    } yield ret

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
