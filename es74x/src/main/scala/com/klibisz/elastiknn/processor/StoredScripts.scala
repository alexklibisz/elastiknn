package com.klibisz.elastiknn.processor

import java.util
import java.util.Collections

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.ElastiKnnVector
import com.klibisz.elastiknn.ElastiKnnVector.Vector
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.script.{Script, ScriptType, StoredScriptSource}

object StoredScripts {

  sealed trait ExactScript[V <: ElastiKnnVector.Vector] {
    def id: String
    def scriptSource: StoredScriptSource
    val putRequest: PutStoredScriptRequest = new PutStoredScriptRequest(
      id,
      "score",
      new BytesArray("{}"),
      XContentType.JSON,
      scriptSource)
    def script(field: String, other: V): Script
  }

  final case class ExactDoubleScript(id: String,
                                     scriptSource: StoredScriptSource)
      extends ExactScript[ElastiKnnVector.Vector.DoubleVector] {
    override def script(field: String, other: Vector.DoubleVector): Script =
      new Script(
        ScriptType.STORED,
        null,
        id,
        util.Map.of("field", field, "other", other.value.values)
      )
  }

  final case class ExactBoolScript(id: String, scriptSource: StoredScriptSource)
      extends ExactScript[ElastiKnnVector.Vector.BoolVector] {
    override def script(field: String, other: Vector.BoolVector): Script =
      new Script(
        ScriptType.STORED,
        null,
        id,
        util.Map.of("field", field, "other", other.value.values)
      )
  }

  protected[elastiknn] val dummyScript = new StoredScriptSource(
    "painless",
    """
      |return 0.0;
      |""".stripMargin,
    Collections.emptyMap()
  )

  // Note on accessing the stored vectors: arrays get sorted when they're indexed, meaning if you access doc['myVec.doubleVector.values'][0]
  // you'll get the smallest element in the original vector, not the 0th element. See this issue: https://github.com/elastic/elasticsearch/issues/49695
  // In order to get the original vector, you have to use params._source['myVec']['doubleVector']['values']. This seems
  // have some performance tradeoffs compared to accessing `doc`. See here:
  // https://www.elastic.co/guide/en/elasticsearch/reference/5.4/modules-scripting-fields.html#modules-scripting-stored
  // There is also a "dense vector" datatype, but it's experimental and doesn't implement a way to access the values
  // in a painless script. So for now we are stuck with accessing params._source. We might come back to this if it proves
  // to be a huge issue, but exact searches are mainly intended as a testing and demo mechanism anyways.

  val exactL1: ExactDoubleScript =
    ExactDoubleScript("elastiknn-exact-l1", dummyScript)

  val exactL2: ExactDoubleScript =
    ExactDoubleScript("elastiknn-exact-l2", dummyScript)

  val exactAngular: ExactDoubleScript = ExactDoubleScript(
    "elastiknn-exact-angular",
    new StoredScriptSource(
      "painless",
      """
        |def a = doc[params.field];
        |def b = params.other;
        |double dotprod = 0.0; // Dot product a and b.
        |double asqsum = 0.0;  // Squared sum of a.
        |double bsqsum = 0.0;  // Squared sum of b.
        |for (int i = 0; i < b.length; i++) {
        |  dotprod += (double) a[i] * b[i];
        |  asqsum += a[i] * a[i];
        |  bsqsum += b[i] * b[i];
        |}
        |double sim = dotprod / (Math.sqrt(asqsum) * Math.sqrt(bsqsum));
        |return 1.0 + sim;
        |""".stripMargin,
      Collections.emptyMap()
    )
  )

  val exactHamming: ExactBoolScript =
    ExactBoolScript("elastiknn-exact-hamming", dummyScript)

  val exactJaccard: ExactBoolScript =
    ExactBoolScript("elastiknn-exact-jaccard", dummyScript)

  val exactScripts: Seq[ExactScript[_]] =
    Seq(exactL1, exactL2, exactAngular, exactHamming, exactJaccard)

}
