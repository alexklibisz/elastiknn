package com.klibisz.elastiknn.codec

import org.apache.lucene.codecs._
import org.apache.lucene.codecs.lucene70.Lucene70DocValuesFormat

class Elastiknn84Codec extends Codec(Elastiknn84Codec.ELASTIKNN_84) {

  private val luceneCodec: Codec = Codec.forName(Elastiknn84Codec.LUCENE_84)

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

object Elastiknn84Codec {
  val ELASTIKNN_84 = "Elastiknn84Codec"
  val LUCENE_84 = "Lucene84"
}
