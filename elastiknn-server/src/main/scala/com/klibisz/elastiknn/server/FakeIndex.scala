package com.klibisz.elastiknn.server

import com.klibisz.elastiknn.api.Mapping

sealed trait FakeIndex {
  def name: String
}

object FakeIndex {

  case class Empty(name: String) extends FakeIndex {}

  case class Open(name: String, mapping: Mapping) extends FakeIndex {}

  case class Closed(name: String) extends FakeIndex {}

}
