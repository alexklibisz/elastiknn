package com.klibisz.ann1b

sealed trait Datatype {
  def sizeOf: Int
  def extension: String
}

object Datatype {
  case object U8Bin extends Datatype {
    override def sizeOf: Int = 1
    override def extension: String = "u8bin"
  }
  case object I8Bin extends Datatype {
    override def sizeOf: Int = 1
    override def extension: String = "i8bin"
  }
  case object FBin extends Datatype {
    override def sizeOf: Int = 4
    override def extension: String = "fbin"
  }
}
