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

  "similarity" - {
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

  "dense float vector" - {
    "roundtrip" in {
      for {
        i <- 0 to 100
        v = Vec.DenseFloat.random(i % 10 + 1)
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

}
