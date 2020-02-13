package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.Similarity
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD

trait ModelOptionsUtils {

  implicit class ModelOptionsImplicits(mopts: ModelOptions) {

    /** Return the processed field name. */
    private[elastiknn] lazy val fieldProc: Option[String] = mopts match {
      case ModelOptions.Exact(_) | ModelOptions.Empty => None
      case ModelOptions.Jaccard(j)                    => Some(j.fieldProcessed)
    }

    private[elastiknn] lazy val similarity: Option[Similarity] = mopts match {
      case ModelOptions.Exact(eopts) => Some(eopts.similarity)
      case ModelOptions.Jaccard(_)   => Some(SIMILARITY_JACCARD)
      case _                         => None
    }

  }

}

object ModelOptionsUtils extends ModelOptionsUtils
