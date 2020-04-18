package views

import models.SearchResult

object utils {

  def resultIsCorrect(result: SearchResult, exactResults: Seq[SearchResult]): Boolean =
    exactResults.exists(_.id == result.id)

  def durationSeconds(durMillis: Long): String = f"${durMillis / 1000f}%.2f seconds"

}
