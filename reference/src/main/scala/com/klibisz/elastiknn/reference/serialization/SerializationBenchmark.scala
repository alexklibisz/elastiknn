package com.klibisz.elastiknn.reference.serialization

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream, ObjectInputStream, ObjectOutputStream}

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.storage.{ByteArrayCodec, ColferSparseBool, ColferUInt32}
import org.nustaq.serialization.FSTConfiguration

import scala.util.Random

object SerializationBenchmark {

  def time[T](msg: String, op: => T): T = {
    val t0 = System.currentTimeMillis()
    val res = op
    println(s"$msg: ${System.currentTimeMillis() - t0} ms")
    res
  }

  def main(args: Array[String]): Unit = {

    val n = 10000
    val m = 10

    implicit val r = new Random(99)
    val vecs = Vec.SparseBool.randoms(4096, n)

    val fstConf = FSTConfiguration.createUnsafeBinaryConfiguration()
    fstConf.registerClass(classOf[Vec.SparseBool])

    for {
      _ <- 0 until m
    } {

      val vecsProto: Seq[Array[Byte]] = time(s"Write proto", vecs.map(v => ByteArrayCodec.sparseBoolVector(v)))
      println(vecsProto.map(_.length).sum)
      time[Unit](s"Read proto", vecsProto.foreach(b => ByteArrayCodec.sparseBoolVector(b).get))

      val vecsFst = time(s"Write FST", vecs.map(fstConf.asByteArray))
      println(vecsFst.map(_.length).sum)
      val checkFst = time(s"Read FST", vecsFst.map { b =>
        fstConf.asObject(b).asInstanceOf[Vec.SparseBool]
      })
      require(vecs == checkFst)

//      val colfBuffer = new Array[Byte](ColferSparseBool.colferSizeMax)
//
//      val vecsColf = time(
//        s"Write colf",
//        vecs.map { v =>
//          val obj = new ColferSparseBool()
//            .withTotalIndices(v.totalIndices)
//            .withTrueIndices(v.trueIndices.map(new ColferUInt32().withValue(_)))
//          val len = obj.marshal(colfBuffer, 0)
//          colfBuffer.take(len)
//        }
//      )
//      println(vecsColf.map(_.length).sum)
//
//      val checkColf = time(
//        "Read colf",
//        vecsColf.map { b =>
//          val colfSbv = new ColferSparseBool()
//          colfSbv.unmarshal(b, 0)
//          Vec.SparseBool(colfSbv.trueIndices.map(_.value), colfSbv.totalIndices)
//        }
//      )
//      assert(vecs == checkColf)

      val vecsOOS = time(
        "Write ObjectOutputStream",
        vecs.map { v =>
          val bout = new ByteArrayOutputStream()
          val oout = new ObjectOutputStream(bout)
          oout.writeObject(v.totalIndices +: v.trueIndices)
          bout.toByteArray
        }
      )
      println(vecsOOS.map(_.length).sum)

      val checkOOS = time(
        "Read ObjectOutputStream",
        vecsOOS.map { b =>
          val bin = new ByteArrayInputStream(b)
          val oin = new ObjectInputStream(bin)
          val arr = oin.readObject.asInstanceOf[Array[Int]]
          Vec.SparseBool(arr.tail, arr.head)
        }
      )
      assert(vecs == checkOOS)

//      val vecsMsgpack: Seq[Array[Byte]] = time(s"Serialize ${vecs.length} to msgpack", vecs.map { v =>
//        writeBinary[Array[Int]](v.trueIndices)
//      })
//      println(vecsMsgpack.map(_.length).sum)
//      time[Unit](s"Deserialize ${vecs.length} from msgpack", vecsMsgpack.foreach(b => readBinary[Array[Int]](b)))

      val vecsDataOutputStream = time(
        s"Write DataOutputStream",
        vecs.map { v =>
          val bout = new ByteArrayOutputStream()
          val dout = new DataOutputStream(bout)
          dout.writeInt(v.trueIndices.length)
          v.trueIndices.foreach(dout.writeShort)
          bout.toByteArray
        }
      )
      println(vecsDataOutputStream.map(_.length).sum)

      val checkDataOutputStream = time(
        s"Read DataOutputStream",
        vecsDataOutputStream.map { b =>
          val bin = new ByteArrayInputStream(b)
          val din = new DataInputStream(bin)
          val len = din.readInt()
          val arr = new Array[Int](len)
          arr.indices.foreach(i => arr.update(i, din.readShort()))
          Vec.SparseBool(arr, 4096)
        }
      )
      require(vecs == checkDataOutputStream)

      println("---")

    }

  }

}
