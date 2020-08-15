package com.klibisz.elastiknn.benchmarks

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

object S3Utils {

  /**
    * Instantiate a minio-compatible s3 client.
    * See minio docs: https://docs.min.io/docs/how-to-use-aws-sdk-for-java-with-minio-server.html
    * @return
    */
  def minioClient(url: String = "http://localhost:9000"): AmazonS3 = {
    val endpointConfig = new EndpointConfiguration(url, "us-east-1")
    val clientConfig = new ClientConfiguration()
    clientConfig.setSignerOverride("AWSS3V4SignerType")
    AmazonS3ClientBuilder.standard
      .withPathStyleAccessEnabled(true)
      .withEndpointConfiguration(endpointConfig)
      .withClientConfiguration(clientConfig)
      .build()
  }

  def defaultClient(): AmazonS3 = AmazonS3ClientBuilder.defaultClient()

  def client(urlOpt: Option[String]): AmazonS3 = urlOpt.map(minioClient).getOrElse(defaultClient())

}
