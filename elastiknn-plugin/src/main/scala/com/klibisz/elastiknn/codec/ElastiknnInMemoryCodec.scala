package com.klibisz.elastiknn.codec

import org.apache.lucene.codecs._
import org.apache.lucene.codecs.memory.{DirectDocValuesFormat, DirectPostingsFormat}

class ElastiknnInMemoryCodec extends Codec(ElastiknnInMemoryCodec.ELASTIKNN_IN_MEMORY) {

  // Codec only overrides a couple members and delegates to Lucene84 for everything else.
  private val lucene84 = Codec.forName("Lucene84")

  override def postingsFormat(): PostingsFormat = new DirectPostingsFormat()

  override def docValuesFormat(): DocValuesFormat = new DirectDocValuesFormat()

  override def storedFieldsFormat(): StoredFieldsFormat = lucene84.storedFieldsFormat()

  override def termVectorsFormat(): TermVectorsFormat = lucene84.termVectorsFormat()

  override def fieldInfosFormat(): FieldInfosFormat = lucene84.fieldInfosFormat()

  override def segmentInfoFormat(): SegmentInfoFormat = lucene84.segmentInfoFormat()

  override def normsFormat(): NormsFormat = lucene84.normsFormat()

  override def liveDocsFormat(): LiveDocsFormat = lucene84.liveDocsFormat()

  override def compoundFormat(): CompoundFormat = lucene84.compoundFormat()

  override def pointsFormat(): PointsFormat = lucene84.pointsFormat()
}

object ElastiknnInMemoryCodec {
  val ELASTIKNN_IN_MEMORY = "ElastiknnInMemoryCodec"
}
