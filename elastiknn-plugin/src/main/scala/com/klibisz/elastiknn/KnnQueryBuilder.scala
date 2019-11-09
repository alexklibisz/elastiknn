package com.klibisz.elastiknn

import java.util

import com.klibisz.elastiknn.utils.CirceUtils._
import io.circe.syntax._
import org.apache.lucene.search.Query
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.functionscore.{ScriptScoreFunctionBuilder, ScriptScoreQueryBuilder}
import org.elasticsearch.index.query.{AbstractQueryBuilder, MatchAllQueryBuilder, QueryParser, QueryShardContext}
import org.elasticsearch.script.{Script, ScriptType}
import scalapb_circe.JsonFormat

object KnnQueryBuilder {

  val NAME = "elastiknn_knn"

  object Parser extends QueryParser[ScriptScoreQueryBuilder] {

    /** This is the first method that gets hit when you run this query. */
    override def fromXContent(parser: XContentParser): ScriptScoreQueryBuilder = {
      // TODO: why does parser.map() work here, but parser.text() throws an exception?
      val json = parser.map.asJson(mapEncoder)
      val query = JsonFormat.fromJson[KNearestNeighborsQuery](json)

      val script = new Script(
        ScriptType.STORED,
        null,
        StoredScripts.exactAngular.id,
        new util.HashMap[String, Object]() {
          put("fieldProc", "vec_proc.exact.vector")
          put("b", util.Arrays.asList(0.11, 0.22))
        }
      )
      val scoreFunctionBuilder = new ScriptScoreFunctionBuilder(script)

//      new KnnQueryBuilder(query)
//      new FunctionScoreQueryBuilder(scoreFunctionBuilder)
      new ScriptScoreQueryBuilder(new MatchAllQueryBuilder(), scoreFunctionBuilder)
//      new MatchAllQueryBuilder()
//      new KnnQueryBuilder()
    }

  }

  object Reader extends Writeable.Reader[ScriptScoreQueryBuilder] {
    override def read(in: StreamInput): ScriptScoreQueryBuilder = {
      throw new Exception("READER")
    }
  }

}

final class KnnQueryBuilder extends MatchAllQueryBuilder

//final class KnnQueryBuilder(query: KNearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {
//
//  override def doWriteTo(out: StreamOutput): Unit = {
//    ???
//  }
//
//  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = {
//    ???
//  }
//
//  /**
//    * Hits here right after Parser#fromXContent. This is supposed to return a Lucene query, and I'm not sure what happens after that.
//    */
//  override def doToQuery(context: QueryShardContext): Query = {
//    // Will need to write the exact queries as script scoring queries.
//    // https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html
//    // The docs show some tempting vector functions (cosineSimilarity, l1Norm, etc.), but those are only available with X-Pack.
//    // This seems to work without any special mappings.
//
//    val script = new Script(
//      ScriptType.STORED,
//      null,
//      StoredScripts.exactAngular.id,
//      new util.HashMap[String, Object]() {
//        put("fieldProc", "vec_raw.exact.vector")
//        put("b", util.Arrays.asList(0.11, 0.22))
//      }
//    )
//    val scoreFunctionBuilder = new ScriptScoreFunctionBuilder(script)
////    val q: ScoreFunction = scoreFunctionBuilder.toFunction(context)
////    new ScriptScoreQuery(???, , 0.0f)
////    new FunctionScoreQuery(new MatchAllDocsQuery(),
////                           scoreFunctionBuilder.toFunction(context),
////                           CombineFunction.MULTIPLY,
////                           0.0f,
////                           Float.MaxValue)
//
////    new FunctionScoreQueryBuilder(scoreFunctionBuilder).toQuery(context)
//
//    ???
//    // If you end up needing to access other documents while executing the query, for example to retrieve the pipeline
//    // that was used to ingest the document, then a similar pattern is used for geo_polygon queries against an indexed
//    // shape. Look at AbstractGeometryQueryBuilder.java in the doRewrite function on line 491. It also looks like you
//    // can access a client using context.registerAsyncAction().
//
////    context.registerAsyncAction(new BiConsumer[Client, ActionListener[_]] {
////      override def accept(client: Client, listener: ActionListener[_]): Unit = {
////        ???
////      }
////    })
//  }
//
//  override def doEquals(other: KnnQueryBuilder): Boolean = {
//    ???
//  }
//
//  override def doHashCode(): Int = {
//    ???
//  }
//
//  override def getWriteableName: String = {
//    ???
//  }
//}
