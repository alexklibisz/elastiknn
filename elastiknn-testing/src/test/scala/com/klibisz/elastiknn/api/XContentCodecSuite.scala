package com.klibisz.elastiknn.api

import org.apache.commons.io.output.ByteArrayOutputStream
import org.elasticsearch.common.xcontent._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class XContentCodecSuite extends AnyFreeSpec with Matchers {

  private implicit val rng: Random = new Random(0)

  private def makeBuilder: (XContentBuilder, () => String) = {
    val bos = new ByteArrayOutputStream()
    val builder = new XContentBuilder(XContentType.JSON.xContent(), bos)
    (builder, () => {
      builder.close()
      new String(bos.toByteArray)
    })
  }

  private def makeParser(jsonString: String): XContentParser = {
    val p = XContentType.JSON
      .xContent()
      .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, jsonString)
    p.nextToken() // Step into the JSON.
    p
  }

  "Similarity" - {
    "roundtrip" in {
      for {
        (str, sim) <- Seq(
          ("jaccard", Similarity.Jaccard),
          ("Jaccard", Similarity.Jaccard),
          ("JACCARD", Similarity.Jaccard),
          ("hamming", Similarity.Hamming),
          ("Hamming", Similarity.Hamming),
          ("HAMMING", Similarity.Hamming),
          ("l1", Similarity.L1),
          ("L1", Similarity.L1),
          ("l2", Similarity.L2),
          ("L2", Similarity.L2),
          ("cosine", Similarity.Cosine),
          ("Cosine", Similarity.Cosine),
          ("COSINE", Similarity.Cosine)
        )
      } {
        val (b, readOnce) = makeBuilder
        XContentCodec.buildUnsafe[Similarity](sim, b)
        val s = readOnce()
        s shouldBe s""""$str"""".toLowerCase()
        val p = makeParser(s)
        XContentCodec.parseUnsafe[Similarity](p) shouldBe sim
      }
    }
  }

  "Vec.DenseFloat" - {
    "roundtrip" in {
      for {
        i <- 0 to 100
        v = Vec.DenseFloat.random(i % 42 + 1)
      } {
        val (b, readOnce) = makeBuilder
        XContentCodec.buildUnsafe[Vec.DenseFloat](v, b)
        val s = readOnce()
        s shouldBe s"""{"values":[${v.values.mkString(",")}]}"""
        // Parse from standard object encoding.
        val p1 = makeParser(s)
        XContentCodec.parseUnsafe[Vec.DenseFloat](p1) shouldBe v
        // Parse from shorthand array encoding.
        val p2 = makeParser(s"""[${v.values.mkString(",")}]""")
        XContentCodec.parseUnsafe[Vec.DenseFloat](p2) shouldBe v
      }
    }
  }

  "Vec.SparseBool" - {
    "roundtrip" in {
      for {
        i <- 0 to 100
        v = Vec.SparseBool.random(i % 42 + 1)
      } {
        val (b, readOnce) = makeBuilder
        XContentCodec.buildUnsafe[Vec.SparseBool](v, b)
        val s = readOnce()
        val fields = Seq(s""""total_indices":${v.totalIndices}""", s""""true_indices":[${v.trueIndices.mkString(",")}]""")
        s shouldBe s"""{${fields.mkString(",")}}"""
        // Parse from standard object encoding.
        val p1 = makeParser(s"""{${rng.shuffle(fields).mkString(",")}}""")
        XContentCodec.parseUnsafe[Vec.SparseBool](p1) shouldBe v
        // Parse from shorthand array encoding.
        val p2 = makeParser(s"""[${v.totalIndices},[${v.trueIndices.mkString(",")}]]""")
        XContentCodec.parseUnsafe[Vec.SparseBool](p2) shouldBe v
      }
    }
  }

  "Vec.Indexed" - {
    "roundtrip" in {
      for {
        i <- 0 to 100
        index = s"index-$i"
        id = s"id-$i"
        field = s"field-$i"
        v = Vec.Indexed(index, id, field)
      } {
        val (b, readOnce) = makeBuilder
        XContentCodec.buildUnsafe[Vec.Indexed](v, b)
        val s = readOnce()
        val fields = Seq(s""""index":"${v.index}"""", s""""id":"${v.id}"""", s""""field":"${v.field}"""")
        s shouldBe s"""{${fields.mkString(",")}}"""
        val p1 = makeParser(s"""{${rng.shuffle(fields).mkString(",")}}""")
        XContentCodec.parseUnsafe[Vec.Indexed](p1) shouldBe v
      }
    }
  }

  "Vec" - {
    "roundtrip" in {
      for {
        i <- 0 to 100
        dfv = Vec.DenseFloat.random(i % 42 + 1)
        sbv = Vec.SparseBool.random(i % 42 + 1)
        iv = Vec.Indexed(s"index-$i", s"id-$i", s"field-$i")
        v = rng.shuffle(Seq(dfv, sbv, iv)).head
      } yield {
        val (b, readOnce) = makeBuilder
        XContentCodec.buildUnsafe[Vec](v, b)
        val s = readOnce()
        val p = makeParser(s)
        XContentCodec.parseUnsafe[Vec](p) shouldBe v
      }
    }
  }

}
