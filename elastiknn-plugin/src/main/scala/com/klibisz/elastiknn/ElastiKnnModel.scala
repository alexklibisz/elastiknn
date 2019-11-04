package com.klibisz.elastiknn

trait ElastiKnnModel {}

class ExactModel extends ElastiKnnModel

object ElastiKnnModel {
  def apply(popts: ProcessorOptions): ElastiKnnModel = new ExactModel
}
