package org.elasticsearch.elastiknn.processor

import java.util
import java.util.Collections

import org.elasticsearch.elastiknn.ElastiKnnVector.Vector
import org.elasticsearch.elastiknn.ElastiKnnVector.Vector.{Empty, FloatVector, SparseBoolVector}
import org.elasticsearch.elastiknn.Similarity.{SIMILARITY_ANGULAR, SIMILARITY_HAMMING, SIMILARITY_JACCARD, SIMILARITY_L1, SIMILARITY_L2}
import org.elasticsearch.elastiknn.{ElastiKnnVector, Similarity, SimilarityAndTypeException, illArgEx}
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.elastiknn.Similarity
import org.elasticsearch.elastiknn.Similarity.{SIMILARITY_ANGULAR, SIMILARITY_HAMMING, SIMILARITY_JACCARD, SIMILARITY_L1, SIMILARITY_L2}
import org.elasticsearch.script.{Script, ScriptType, StoredScriptSource}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

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

  val exactL1: ExactDoubleScript = ExactDoubleScript(
    "elastiknn-exact-l1",
    """
      |def a = doc[params.field];
      |def b = params.other;
      |double sumabsdiff = 0.0;
      |for (int i = 0; i < b.length; i++) {
      |  sumabsdiff += Math.abs(a[i] - b[i]);
      |}
      |return 1.0 / (sumabsdiff + 1e-6);
      |""".stripMargin
  )

  val exactL2: ExactDoubleScript =
    ExactDoubleScript(
      "elastiknn-exact-l2",
      """
        |def a = doc[params.field];
        |def b = params.other;
        |double sumsqdiff = 0.0;
        |for (int i = 0; i < b.length; i++) {
        |  sumsqdiff += Math.pow(a[i] - b[i], 2);
        |}
        |double dist = Math.sqrt(sumsqdiff);
        |return 1.0 / (dist + 1e-6);
        |""".stripMargin
    )

  val exactAngular: ExactDoubleScript = ExactDoubleScript(
    "elastiknn-exact-angular",
    """
      |def a = doc[params.field];
      |def b = params.other;
      |double dotprod = 0.0; // Dot product a and b.
      |double asqsum = 0.0;  // Squared sum of a.
      |double bsqsum = 0.0;  // Squared sum of b.
      |for (int i = 0; i < b.length; i++) {
      |  dotprod += a[i] * b[i];
      |  asqsum += a[i] * a[i];
      |  bsqsum += b[i] * b[i];
      |}
      |double sim = dotprod / (Math.sqrt(asqsum) * Math.sqrt(bsqsum));
      |return 1.0 + sim;
      |""".stripMargin
  )

  val exactHamming: ExactBoolScript =
    ExactBoolScript(
      "elastiknn-exact-hamming",
      """
        |def a = doc[params.field];
        |Map bTrueIndices = params.bTrueIndices;
        |int totalCount = a.get(-1);
        |int aTrueCount = a.size();
        |int bTrueCount = bTrueIndices.size();
        |int eqTrueCount = 0;
        |for (i in a) if (bTrueIndices.containsKey(i.toString())) eqTrueCount += 1;
        |double neqTrueCount = Math.max(aTrueCount - eqTrueCount, 0) + Math.max(bTrueCount - eqTrueCount, 0);
        |return (totalCount - neqTrueCount) / totalCount;
        |""".stripMargin
    )

  val exactJaccard: ExactBoolScript =
    ExactBoolScript(
      "elastiknn-exact-jaccard",
      """
        |def a = doc[params.field];
        |Map bTrueIndices = params.bTrueIndices;
        |double isec = 0;
        |for (i in a) if (bTrueIndices.containsKey(i.toString())) isec += 1;
        |return isec / (a.size() + bTrueIndices.size() - isec);
        |""".stripMargin
    )

  val exactScripts: Seq[ExactScript[_]] =
    Seq(exactL1, exactL2, exactAngular, exactHamming, exactJaccard)

  def exact(similarity: Similarity, fieldRaw: String, elastiKnnVector: ElastiKnnVector): Try[Script] =
    (similarity, elastiKnnVector.vector) match {
      case (SIMILARITY_ANGULAR, dvec: FloatVector) =>
        Success(StoredScripts.exactAngular.script(fieldRaw, dvec))
      case (SIMILARITY_L1, dvec: FloatVector) =>
        Success(StoredScripts.exactL1.script(fieldRaw, dvec))
      case (SIMILARITY_L2, dvec: FloatVector) =>
        Success(StoredScripts.exactL2.script(fieldRaw, dvec))
      case (SIMILARITY_HAMMING, bvec: SparseBoolVector) =>
        Success(StoredScripts.exactHamming.script(fieldRaw, bvec))
      case (SIMILARITY_JACCARD, bvec: SparseBoolVector) =>
        Success(StoredScripts.exactJaccard.script(fieldRaw, bvec))
      case (_, Empty) => Failure(illArgEx("Must provide vector"))
      case (_, _)     => Failure(SimilarityAndTypeException(similarity, elastiKnnVector))
    }

}
