//package com.klibisz.elastiknn.storage
//
//import scala.util.{Failure, Try}
//
//private[storage] object UnsafeSerialization {
//
//  /**
//    * Field f =Unsafe.class.getDeclaredField("theUnsafe");
//    *    f.setAccessible(true);
////    * unsafe = (Unsafe) f.get(null);
//    */
//  private val unsafe = Try {
//    import sun.misc.Unsafe
//    val f = classOf[Unsafe].getDeclaredField("theUnsafe")
//    f.setAccessible(true)
//    f.get(null).asInstanceOf[Unsafe]
//  }.recoverWith {
//    case ex => Failure(new RuntimeException(s"Failed to initialize instance of sun.misc.Unsafe", ex))
//  }.get
//
//  def writeInts(iarr: Array[Int]): Array[Byte] = {
//
////    val barr = new Array[Byte](iarr.length * 4)
////    unsafe.copyMemory(iarr, 0, barr, 0, iarr.length * 4)
////    unsafe.copyMemory(iarr, 16, barr, 20, iarr.length * 4)
////    unsafe.putInt(barr, 0, iarr.length)
////    var i = 0
////    while (i < iarr.length) {
////      unsafe.putInt(barr, (i + 1) * 4L, iarr(i))
////      i += 1
////    }
////    barr
//  }
//
//  def readInts(barr: Array[Byte]): Array[Int] = {
//    val iarr = new Array[Int](12)
//    unsafe.copyMemory(barr, 0, iarr, 0, 12 * 4)
//    iarr
//  }
//
//}
