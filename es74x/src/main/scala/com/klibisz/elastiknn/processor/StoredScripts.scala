package com.klibisz.elastiknn.processor

import java.util
import java.util.Collections

import com.klibisz.elastiknn.ElastiKnnVector
import com.klibisz.elastiknn.ElastiKnnVector.Vector
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.script.{Script, ScriptType, StoredScriptSource}

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

  final case class ExactBoolScript(id: String, script: String) extends ExactScript[ElastiKnnVector.Vector.BoolVector] {
    override def script(field: String, other: Vector.BoolVector): Script =
      new Script(
        ScriptType.STORED,
        null,
        id,
        util.Map.of("field", field, "other", other.value.values)
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
        |def b = params.other;
        |double eq = 0;
        |for (int i = 0; i < b.length; i++) {
        |  if (a[i] == b[i]) eq += 1;
        |}
        |return eq / b.length;
        |""".stripMargin
    )

  val exactJaccard: ExactBoolScript =
    ExactBoolScript(
      "elastiknn-exact-jaccard",
      """
        |def a = doc[params.field];
        |def b = params.other;
        |double isec = 0;
        |double asum = 0;
        |double bsum = 0;
        |for (int i = 0; i < b.length; i++) {
        |  if (a[i] && b[i]) isec += 1;
        |  if (a[i]) asum += 1;
        |  if (b[i]) bsum += 1;
        |}
        |double sim = isec / (asum + bsum - isec);
        |return sim;
        |""".stripMargin
    )

  val exactScripts: Seq[ExactScript[_]] =
    Seq(exactL1, exactL2, exactAngular, exactHamming, exactJaccard)

}
