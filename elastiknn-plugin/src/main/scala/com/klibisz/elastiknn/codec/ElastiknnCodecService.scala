package com.klibisz.elastiknn.codec

import org.apache.lucene.codecs.Codec
import org.elasticsearch.index.codec.CodecService

class ElastiknnCodecService extends CodecService(null, null) {

  override def codec(name: String): Codec =
    Codec.forName(Elastiknn84Codec.ELASTIKNN_84)

}
