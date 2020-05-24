package com.klibisz.elastiknn.benchmarks

import java.util.zip.GZIPInputStream

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.S3ObjectInputStream

object ReadGzipFromS3Example extends App {

  val s3 = AmazonS3ClientBuilder.defaultClient()
  val obj = s3.getObject("elastiknn-benchmarks", "data/processed/annbfashionmnist/vecs.json.gz")
  val inputStream: S3ObjectInputStream = obj.getObjectContent
  val gzipInputStream = new GZIPInputStream(inputStream)
  val src = scala.io.Source.fromInputStream(gzipInputStream)
  val lines = src.getLines()
  for (line <- lines) {
    println(line)
  }

}
