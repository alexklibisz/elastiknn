package models

import com.klibisz.elastiknn.api._

case class Dataset(name: String, url: String, examples: Seq[Example])

case class Example(name: String, index: String, mapping: Seq[Mapping], query: Seq[NearestNeighborsQuery])

