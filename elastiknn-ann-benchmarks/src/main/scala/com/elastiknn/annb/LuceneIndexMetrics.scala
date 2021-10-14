package com.elastiknn.annb

import org.apache.commons.io.FileUtils
import org.apache.commons.math3.stat.StatUtils
import org.apache.lucene.index._
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.MMapDirectory

import java.nio.file.Path
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

final case class DistributionSummary(
    mean: Float,
    variance: Float,
    min: Long,
    p10: Long,
    p20: Long,
    p30: Long,
    p40: Long,
    p50: Long,
    p60: Long,
    p70: Long,
    p80: Long,
    p90: Long,
    p99: Long,
    max: Long
)

final case class FieldMetrics(
    /** Name of the field. */
    fieldName: String,
    /** Total number of documents with a term for this field. */
    numDocs: Long,
    /** Total number of terms stored for this field, across all segments. */
    numTermsStored: Long,
    /** Total number of _unique_ terms stored for this field, across all segments. */
    numDistinctTerms: Long,
    /**
      * Terms stored divided by distinct terms.
      * The higher the value, the more segments need to be visited to match this term.
      * A value of 1 means each term occurs in only one segment.
      * A much higher value might indicate the terms should be partitioned more carefully among segments.
      */
    termRedundancy: Float,
    /**
      * Summary statistics for the documents per term.
      * A large spread and/or variance indicates there are some very frequent terms and some very in-frequent terms.
      */
    termFrequency: DistributionSummary
)

final case class LuceneIndexMetrics(numSegments: Int, numDocs: Long, sizeOnDiskGb: Float, fieldStatistics: Seq[FieldMetrics]) {
  import io.circe.generic.auto._
  import io.circe.syntax._
  def prettySortedJson: String = this.asJson.spaces2
}

object LuceneIndexMetrics {
  def apply(indexDirectoryPath: Path): LuceneIndexMetrics = {
    val mmapDirectory = new MMapDirectory(indexDirectoryPath)
    val indexReader = DirectoryReader.open(mmapDirectory)
    try {
      val indexSearcher = new IndexSearcher(indexReader)
      val numSegments = indexReader.leaves().size()
      val leaves = indexReader.leaves().iterator().asScala.toList
      val fields = leaves.flatMap(_.reader().getFieldInfos.asScala.map(_.name)).distinct.sorted
      val fieldStatistics = for {
        field <- fields
        collectionStats = indexSearcher.collectionStatistics(field)
        if collectionStats != null
      } yield {
        val numTermsStored = leaves.map(_.reader().terms(field).size()).sum
        val termToFrequency: Map[String, Double] = leaves
          .map(_.reader())
          .flatMap { r =>
            val terms = r.terms(field)
            val termsEnum = terms.iterator()
            // Actually have to use string here to get accurate metrics.
            // Other unique identifiers, e.g., termsEnum.term().hashCode, seem to produce collisions.
            val arrayBuffer = new ArrayBuffer[(String, Int)](terms.size().toInt)
            while (termsEnum.next() != null) {
              arrayBuffer.append((termsEnum.term().utf8ToString(), termsEnum.docFreq()))
            }
            arrayBuffer.toList
          }
          .groupBy(_._1)
          .mapValues(_.map(_._2).sum.toDouble)
        val numDistinctTerms = termToFrequency.size
        val docsPerTerm = termToFrequency.values.toArray
        FieldMetrics(
          fieldName = field,
          numTermsStored = numTermsStored,
          numDistinctTerms = numDistinctTerms,
          termRedundancy = numTermsStored * 1f / numDistinctTerms,
          numDocs = collectionStats.docCount(),
          termFrequency = DistributionSummary(
            StatUtils.mean(docsPerTerm).toFloat,
            StatUtils.variance(docsPerTerm).toFloat,
            docsPerTerm.min.toLong,
            StatUtils.percentile(docsPerTerm, 10).toLong,
            StatUtils.percentile(docsPerTerm, 20).toLong,
            StatUtils.percentile(docsPerTerm, 30).toLong,
            StatUtils.percentile(docsPerTerm, 40).toLong,
            StatUtils.percentile(docsPerTerm, 50).toLong,
            StatUtils.percentile(docsPerTerm, 60).toLong,
            StatUtils.percentile(docsPerTerm, 70).toLong,
            StatUtils.percentile(docsPerTerm, 80).toLong,
            StatUtils.percentile(docsPerTerm, 90).toLong,
            StatUtils.percentile(docsPerTerm, 99).toLong,
            docsPerTerm.max.toLong
          )
        )
      }
      val sizeOnDiskGb = FileUtils.sizeOfDirectory(indexDirectoryPath.toFile) / 1e9.toFloat
      LuceneIndexMetrics(
        numSegments,
        indexReader.numDocs(),
        sizeOnDiskGb,
        fieldStatistics
      )
    } finally {
      indexReader.close()
      mmapDirectory.close()
    }
  }
}
