package org.elasticsearch.elastiknn.processor

import java.util
import java.util.Collections

import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.elastiknn.ElastiKnnVector.Vector
import org.elasticsearch.elastiknn.ElastiKnnVector.Vector.Empty
import org.elasticsearch.elastiknn.{ElastiKnnVector, Similarity, SimilarityAndTypeException, illArgEx}
import org.elasticsearch.script.{Script, ScriptType, StoredScriptSource}

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

object StoredScripts {

  sealed trait ExactScript[V <: ElastiKnnVector.Vector] {
    def id: String
    def script: String
    val putRequest: PutStoredScriptRequest = new PutStoredScriptRequest(id,
                                                                        "score",
                                                                        new BytesArray("{}"),
                                                                        XContentType.JSON,
                                                                        new StoredScriptSource(
                                                                          "painless",
                                                                          script,
                                                                          Collections.emptyMap()
                                                                        ))
    def script(field: String, other: V): Script
  }

  final case class ExactDoubleScript(id: String, script: String) extends ExactScript[ElastiKnnVector.Vector.FloatVector] {
    override def script(field: String, other: Vector.FloatVector): Script =
      new Script(
        ScriptType.STORED,
        null,
        id,
        util.Map.of("field", field, "other", other.value.values)
      )
  }

  final case class ExactBoolScript(id: String, script: String) extends ExactScript[ElastiKnnVector.Vector.SparseBoolVector] {
    override def script(field: String, other: Vector.SparseBoolVector): Script =
      new Script(
        ScriptType.STORED,
        null,
        id,
        util.Map.of("field", field, "bTrueIndices", other.value.trueIndices.map(_.toString -> null).toMap.asJava)
      )
  }

  val exactScripts: Seq[ExactScript[_]] = Seq.empty

  def exact(similarity: Similarity, fieldRaw: String, elastiKnnVector: ElastiKnnVector): Try[Script] =
    (similarity, elastiKnnVector.vector) match {
      case (_, Empty) => Failure(illArgEx("Must provide vector"))
      case (_, _)     => Failure(SimilarityAndTypeException(similarity, elastiKnnVector))
    }

}
