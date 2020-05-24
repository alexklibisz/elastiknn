package com.klibisz.elastiknn.reference.lucene

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.storage.ByteArrayCodec
import upickle.default._

import scala.util.Random

object SerializationBenchmark {

  def time[T](msg: String, op: => T): T = {
    val t0 = System.currentTimeMillis()
    val res = op
    println(s"$msg: ${System.currentTimeMillis() - t0} ms")
    res
  }

  def main(args: Array[String]): Unit = {

    val n = 200000
    val m = 20

    for {
      _ <- 0 until m
    } {

      implicit val r = new Random(99)
      val vecs = Vec.SparseBool.randoms(1000, n)

//      val vecsProto: Seq[Array[Byte]] = time(s"Serialize ${vecs.length} to proto", vecs.map(v => ByteArrayCodec.sparseBoolVector(v)))
//      println(vecsProto.map(_.length).sum)
//
//      time[Unit](s"Deserialize ${vecs.length} from proto", vecsProto.foreach(b => ByteArrayCodec.sparseBoolVector(b).get))
//
//      val vecsMsgpack: Seq[Array[Byte]] = time(s"Serialize ${vecs.length} to msgpack", vecs.map { v =>
//        writeBinary[Array[Int]](v.trueIndices)
//      })
//      println(vecsMsgpack.map(_.length).sum)
//
//      time[Unit](s"Deserialize ${vecs.length} from msgpack", vecsMsgpack.foreach(b => readBinary[Array[Int]](b)))

      import com.klibisz.elastiknn.serialization

      val vecsDataOutputStream = time(
        s"Serialize ${vecs.length} to DataOutputStream",
        vecs.map { v =>
//          val bout = new ByteArrayOutputStream()
//          val dout = new DataOutputStream(bout)
//          dout.writeInt(v.totalIndices)
//          dout.writeInt(v.trueIndices.length)
//          v.trueIndices.foreach(dout.writeInt)
//          val barr = bout.toByteArray
//          dout.close()
//          barr
          serialization.ByteArrayCodec.writeInts(Array(v.totalIndices, v.trueIndices.length) ++ v.trueIndices)
        }
      )
      println(vecsDataOutputStream.map(_.length).sum)

      time[Unit](
        s"Deserialize ${vecs.length} from DataOutputStream",
        vecsDataOutputStream.foreach { b =>
          val bin = new ByteArrayInputStream(b)
          val din = new DataInputStream(bin)
          val total = din.readInt()
          din.readInt()
          val arr = Array.ofDim[Int](din.readInt())
          var i = 0
          while (i < arr.length) {
            arr.update(i, din.readInt())
            i += 1
          }
          Vec.SparseBool(arr, total)
        }
      )

      println("---")

    }

  }

}
