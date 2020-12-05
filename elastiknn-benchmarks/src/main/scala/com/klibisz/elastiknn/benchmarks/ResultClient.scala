package com.klibisz.elastiknn.benchmarks

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery}
import com.klibisz.elastiknn.benchmarks.codecs._
import io.circe.parser.decode
import io.circe.syntax._
import zio._
import zio.blocking._
import zio.stream._

import scala.annotation.tailrec
import scala.collection.JavaConverters._

trait ResultClient {
  def find(experiment: Experiment, query: Query): IO[Throwable, Option[BenchmarkResult]]
  def save(result: BenchmarkResult): IO[Throwable, Unit]
  def all(): Stream[Throwable, BenchmarkResult]
}

object ResultClient {

  def s3(bucket: String, keyPrefix: String): ZLayer[Has[AmazonS3] with Blocking, Nothing, Has[ResultClient]] = {

    def genKey(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int, shards: Int): String = {

      def mappingString(mapping: Mapping): String = mapping match {
        case Mapping.SparseBool(_)                   => s"m_sparsebool/shards_${shards}"
        case Mapping.SparseIndexed(_)                => s"m_sparseindexed/shards_${shards}"
        case Mapping.JaccardLsh(_, l, k)             => s"m_jaccardlsh/shards_${shards}/L_$l/k_$k"
        case Mapping.HammingLsh(_, l, k)             => s"m_hamminglsh/shards_${shards}/L_$l/k_$k"
        case Mapping.DenseFloat(_)                   => s"m_densefloat"
        case Mapping.AngularLsh(_, l, k)             => s"m_angularlsh/shards_${shards}/L_$l/k_$k"
        case Mapping.L2Lsh(_, l, k, w)               => s"m_l2lsh/shards_${shards}/L_$l/k_$k"
        case Mapping.PermutationLsh(_, k, repeating) => s"m_permutationlsh/shards_${shards}/k_$k/repeating_$repeating"
      }

      def queryString(query: NearestNeighborsQuery): String =
        query match {
          case NearestNeighborsQuery.Exact(_, similarity, _) =>
            s"q_exact/sim_$similarity/k_${k}/shards_${shards}"
          case NearestNeighborsQuery.SparseIndexed(_, similarity, _) =>
            s"q_sparseindexed/sim_$similarity/k_${k}/shards_${shards}"
          case nnq: NearestNeighborsQuery.ApproximateQuery =>
            nnq match {
              case NearestNeighborsQuery.JaccardLsh(_, candidates, _, limit) =>
                s"q_jaccardlsh/candidates_$candidates/limit_$limit/k_${k}"
              case NearestNeighborsQuery.HammingLsh(_, candidates, _, limit) =>
                s"q_hamminglsh/candidates_$candidates/limit_$limit/k_${k}"
              case NearestNeighborsQuery.AngularLsh(_, candidates, _, limit) =>
                s"q_angularlsh/candidates_$candidates/limit_$limit/k_${k}"
              case NearestNeighborsQuery.L2Lsh(_, candidates, probes, _, limit) =>
                s"q_l2lsh/candidates_$candidates/probes_$probes/limit_$limit/k_${k}"
              case NearestNeighborsQuery.PermutationLsh(_, similarity, candidates, _, limit) =>
                s"q_permutationlsh/sim_$similarity/candidates_$candidates/limit_$limit/k_${k}"
            }
        }

      val suffix = s"${dataset.toString}/${mappingString(mapping)}/${queryString(query)}.json"
      if (keyPrefix.nonEmpty) s"$keyPrefix/$suffix"
      else suffix
    }

    ZLayer.fromServices[AmazonS3, Blocking.Service, ResultClient] {
      case (client, blocking) =>
        new ResultClient {
          override def find(experiment: Experiment, query: Query): IO[Throwable, Option[BenchmarkResult]] = {
            val key = genKey(experiment.dataset, experiment.mapping, query.nnq, query.k, experiment.shards)
            for {
              ex <- blocking.effectBlocking(client.doesObjectExist(bucket, key))
              res: Option[BenchmarkResult] <- if (ex) {
                for {
                  body <- blocking.effectBlocking(client.getObjectAsString(bucket, key))
                  dec <- ZIO.fromEither(decode[BenchmarkResult](body))
                } yield Some(dec)
              } else ZIO.effectTotal(None)
            } yield res
          }

          override def save(result: BenchmarkResult): IO[Throwable, Unit] = {
            val key = genKey(result.dataset, result.mapping, result.query, result.k, result.shards)
            for {
              bucketExists <- blocking.effectBlocking(client.doesBucketExistV2(bucket))
              _ <- if (bucketExists) ZIO.succeed(()) else blocking.effectBlocking(client.createBucket(bucket))
              _ <- blocking.effectBlocking(client.putObject(bucket, key, result.asJson.spaces2SortKeys))
            } yield ()
          }

          override def all(): Stream[Throwable, BenchmarkResult] = {

            @tailrec
            def readAllKeys(req: ListObjectsV2Request, agg: Vector[String] = Vector.empty): Vector[String] = {
              val res = client.listObjectsV2(req)
              val keys = res.getObjectSummaries.asScala.toVector.map(_.getKey).filter(_.endsWith(".json"))
              if (res.isTruncated) readAllKeys(req.withContinuationToken(res.getNextContinuationToken), agg ++ keys)
              else agg ++ keys
            }

            val req = new ListObjectsV2Request().withBucketName(bucket).withPrefix(keyPrefix)

            Stream
              .fromIterableM(blocking.effectBlocking(readAllKeys(req)))
              .mapM(key => blocking.effectBlocking(client.getObjectAsString(bucket, key)))
              .mapM(body => ZIO.fromEither(decode[BenchmarkResult](body)))
          }
        }
    }

  }

}
