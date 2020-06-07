package com.klibisz.elastiknn.storage

import java.security.{AccessController, PrivilegedAction}

import org.nustaq.serialization.FSTConfiguration

private[storage] object FST {

  val conf: FSTConfiguration = AccessController.doPrivileged(new PrivilegedAction[FSTConfiguration] {
    override def run(): FSTConfiguration = FSTConfiguration.createUnsafeBinaryConfiguration()
  })
  conf.registerClass(classOf[Array[Int]])
  conf.registerClass(classOf[Array[Float]])

}
