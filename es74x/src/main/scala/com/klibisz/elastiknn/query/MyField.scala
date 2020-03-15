package com.klibisz.elastiknn.query

import java.io.Reader

import org.apache.lucene.analysis.{Analyzer, TokenStream}
import org.apache.lucene.index.{IndexableField, IndexableFieldType}
import org.apache.lucene.util.BytesRef

class MyIndexableField extends IndexableField {
  override def name(): String = ???

  override def fieldType(): IndexableFieldType = ???

  override def tokenStream(analyzer: Analyzer, reuse: TokenStream): TokenStream = ???

  override def binaryValue(): BytesRef = ???

  override def stringValue(): String = ???

  override def readerValue(): Reader = ???

  override def numericValue(): Number = ???
}
