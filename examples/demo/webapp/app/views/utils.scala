package views

import models.SearchResult

object utils {

  def resultIsCorrect(result: SearchResult, exactResults: Seq[SearchResult]): Boolean =
    exactResults.exists(_.id == result.id)

}
