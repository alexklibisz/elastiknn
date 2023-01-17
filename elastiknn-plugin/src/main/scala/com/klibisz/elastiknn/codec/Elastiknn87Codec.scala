package com.klibisz.elastiknn.codec

import org.apache.lucene.codecs._
import org.apache.lucene.codecs.lucene70.Lucene70DocValuesFormat
import org.apache.lucene.codecs.lucene87.Lucene87Codec

class Elastiknn87Codec extends Codec(ElastiknnCodecService.ELASTIKNN_87) {
  private val luceneCodec: Codec = new Lucene87Codec()
  override def docValuesFormat(): DocValuesFormat = new Lucene70DocValuesFormat()
  override def postingsFormat(): PostingsFormat = luceneCodec.postingsFormat()
  override def storedFieldsFormat(): StoredFieldsFormat = luceneCodec.storedFieldsFormat()
  override def termVectorsFormat(): TermVectorsFormat = luceneCodec.termVectorsFormat()
  override def fieldInfosFormat(): FieldInfosFormat = luceneCodec.fieldInfosFormat()
  override def segmentInfoFormat(): SegmentInfoFormat = luceneCodec.segmentInfoFormat()
  override def normsFormat(): NormsFormat = luceneCodec.normsFormat()
  override def liveDocsFormat(): LiveDocsFormat = luceneCodec.liveDocsFormat()
  override def compoundFormat(): CompoundFormat = luceneCodec.compoundFormat()
  override def pointsFormat(): PointsFormat = luceneCodec.pointsFormat()
}