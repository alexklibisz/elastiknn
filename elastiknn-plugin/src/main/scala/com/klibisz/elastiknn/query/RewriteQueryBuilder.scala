package com.klibisz.elastiknn.query

import org.apache.lucene.search.Query
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.index.query._

/**
  * Use this class to defer an operation until the next rewrite step. For example, if you have a SetOnce supplier,
  * you shouldn't immediately call .get() on it. Instead, you can pass an anonymous function to this class and it will
  * call the function (which calls .get()) on the next re-write step.
  *
  * @param f An anonymous function that takes a rewrite context and returns a QueryBuilder.
  */
private[query] case class RewriteQueryBuilder(f: QueryRewriteContext => QueryBuilder) extends AbstractQueryBuilder[RewriteQueryBuilder] {
  override def doRewrite(c: QueryRewriteContext): QueryBuilder = f(c)
  private val ex = new UnsupportedOperationException("Only supports doRewrite")
  def doWriteTo(out: StreamOutput): Unit = throw ex
  def doXContent(b: XContentBuilder, p: ToXContent.Params): Unit = throw ex
  def doToQuery(context: SearchExecutionContext): Query = throw ex
  def doEquals(other: RewriteQueryBuilder): Boolean = throw ex
  def doHashCode(): Int = throw ex
  def getWriteableName: String = throw ex
}
