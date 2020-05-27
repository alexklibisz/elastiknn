package com.klibisz.elastiknn.reference.serialization

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.storage.ByteArrayCodec
import com.klibisz.elastiknn.serialization.BinaryCodecs
import jdk.internal.misc.Unsafe

import scala.util.Random

object SerializationBenchmark {

  def time[T](msg: String, op: => T): T = {
    val t0 = System.currentTimeMillis()
    val res = op
    println(s"$msg: ${System.currentTimeMillis() - t0} ms")
    res
  }

  def main(args: Array[String]): Unit = {

    val n = 20000
    val m = 200

    implicit val r = new Random(99)
    val vecs = Vec.SparseBool.randoms(1000, n)

    for {
      _ <- 0 until m
    } {

      val vecsProto: Seq[Array[Byte]] = time(s"Serialize ${vecs.length} to proto", vecs.map(v => ByteArrayCodec.sparseBoolVector(v)))
      println(vecsProto.map(_.length).sum)
      time[Unit](s"Deserialize ${vecs.length} from proto", vecsProto.foreach(b => ByteArrayCodec.sparseBoolVector(b).get))
//
//      val vecsMsgpack: Seq[Array[Byte]] = time(s"Serialize ${vecs.length} to msgpack", vecs.map { v =>
//        writeBinary[Array[Int]](v.trueIndices)
//      })
//      println(vecsMsgpack.map(_.length).sum)
//
//      time[Unit](s"Deserialize ${vecs.length} from msgpack", vecsMsgpack.foreach(b => readBinary[Array[Int]](b)))

      val vecsDataOutputStream = time(
        s"Serialize ${vecs.length} to DataOutputStream",
        vecs.map { v =>
          BinaryCodecs.writeInts(v.totalIndices +: v.trueIndices)
        }
      )
      println(vecsDataOutputStream.map(_.length).sum)

      val checkDataOutputStream = time(
        s"Deserialize ${vecs.length} from DataOutputStream",
        vecsDataOutputStream.map { b =>
          val ints = BinaryCodecs.readInts(b)
          Vec.SparseBool(ints.tail, ints.head)
        }
      )
      require(vecs == checkDataOutputStream)

      println("---")

    }

  }

}
