package com.klibisz.elastiknn

import scalapb.GeneratedMessage
import scalapb_circe.JsonFormat

package object requests {

  final case class AcknowledgedResponse(acknowledged: Boolean)

  final case class Processor(name: String, configuration: String)

  object Processor {
    def apply(name: String, configuration: GeneratedMessage): Processor =
      Processor(name, JsonFormat.toJsonString(configuration))
  }

  final case class PutPipelineRequest(id: String, description: String, processors: Seq[Processor] = Seq.empty)

  object PutPipelineRequest {
    def apply(id: String, description: String, processor: Processor): PutPipelineRequest =
      PutPipelineRequest(id, description, Seq(processor))
  }

  final case class PrepareMappingRequest(index: String, processorOptions: ProcessorOptions)

}
