package com.klibisz.elastiknn.codec

import org.apache.lucene.backward_codecs.lucene87.Lucene87Codec
import org.apache.lucene.codecs._

/**
  * No longer used as of Elasticsearch 8.0.0. Kept for backwards-compatibility.
  */
class Elastiknn88Codec extends Codec("Elastiknn88Codec") {
  private val luceneCodec: Codec = new Lucene87Codec(Lucene87Codec.Mode.BEST_SPEED)
  override def docValuesFormat(): DocValuesFormat = luceneCodec.docValuesFormat()
  override def postingsFormat(): PostingsFormat = luceneCodec.postingsFormat()
  override def storedFieldsFormat(): StoredFieldsFormat = luceneCodec.storedFieldsFormat()
  override def termVectorsFormat(): TermVectorsFormat = luceneCodec.termVectorsFormat()
  override def fieldInfosFormat(): FieldInfosFormat = luceneCodec.fieldInfosFormat()
  override def segmentInfoFormat(): SegmentInfoFormat = luceneCodec.segmentInfoFormat()
  override def normsFormat(): NormsFormat = luceneCodec.normsFormat()
  override def liveDocsFormat(): LiveDocsFormat = luceneCodec.liveDocsFormat()
  override def compoundFormat(): CompoundFormat = luceneCodec.compoundFormat()
  override def pointsFormat(): PointsFormat = luceneCodec.pointsFormat()
  override def knnVectorsFormat(): KnnVectorsFormat = luceneCodec.knnVectorsFormat()
}
