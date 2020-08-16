package com.klibisz.elastiknn.server

import com.klibisz.elastiknn.api.Mapping
import org.apache.lucene.search.IndexSearcher

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

final class FakeElasticsearch {

  private val indices: mutable.Map[String, FakeIndex] = mutable.Map.empty

  def createIndex(index: String): Try[Unit] =
    if (indices.contains(index)) Failure(new IllegalStateException(s"Index [$index] already exists"))
    else Success(indices.put(index, new FakeIndex.Empty(index)))

}
