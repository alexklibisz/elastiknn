package com.klibisz.ann1b

final case class Dataset(name: String, dims: Int, dtype: Datatype)

object Dataset {

  final case class Doc(id: String, vec: Array[Float])

  object Doc {
    def apply(index: Long, vec: Array[Float]): Doc = Doc(s"v$index", vec)
  }

  // Default provided datasets from http://big-ann-benchmarks.com/.
  val bigann = new Dataset("bigann", 128, Datatype.U8Bin)
  val microsoftSpaceV1b = new Dataset("msft-space-v1b", 100, Datatype.I8Bin)
  val yandexTextToImage1B = new Dataset("yandex-text-to-image-1b", 200, Datatype.FBin)

}
