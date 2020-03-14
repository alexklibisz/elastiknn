package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.Similarity
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD

trait ModelOptionsUtils {

  implicit class ModelOptionsImplicits(mopts: ModelOptions) {

    /** Return the processed field name. */
    private[elastiknn] lazy val fieldProc: Option[String] = mopts match {
      case ModelOptions.ExactComputed(_) | ModelOptions.Empty => None
      case ModelOptions.JaccardLsh(jacc)                      => Some(jacc.fieldProcessed)
      case ModelOptions.JaccardIndexed(jaccix)                => Some(jaccix.fieldProcessed)
    }

  }

}

object ModelOptionsUtils extends ModelOptionsUtils
