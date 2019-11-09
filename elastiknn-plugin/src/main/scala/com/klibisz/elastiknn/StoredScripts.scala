package com.klibisz.elastiknn

import java.util.Collections

import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.script.StoredScriptSource

object StoredScripts {

  val exactAngular: PutStoredScriptRequest = new PutStoredScriptRequest(
    "elastiknn-exact-angular",
    "score",
    new BytesArray("{}"),
    XContentType.JSON,
    new StoredScriptSource(
      "painless",
//      "return 99.0;",
      """
        |def a = doc[params.fieldProc];
        |def b = params.b;
        |double dp = 0.0, an = 0.0, bn = 0.0;
        |for (int i = 0; i < a.length; i++) {
        |  dp += a[i] * b[i];
        |  an += Math.pow(a[i], 2);
        |  bn += Math.pow(b[i], 2);
        |}
        |double sim = dp / (Math.sqrt(an) * Math.sqrt(bn));
        |return 1.0 + sim;
        |""".stripMargin,
      Collections.emptyMap()
    )
  )

}
