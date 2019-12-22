import json
from typing import List, Dict

from dataclasses import dataclass
from dataclasses_json import dataclass_json
from elasticsearch import Elasticsearch
from google.protobuf.json_format import MessageToDict

from . import ELASTIKNN_NAME
from .elastiknn_pb2 import ProcessorOptions, ExactModelOptions, SIMILARITY_JACCARD


@dataclass_json
@dataclass
class PutPipelineRequest:
    description: str
    processors: List[Dict]


class ElastiKnnClient(object):

    def __init__(self, hosts: List[str] = ["http://localhost:9200"]):
        self.hosts = hosts
        self.es = Elasticsearch(self.hosts)

    def setup_cluster(self):
        # URL argument has to start with a /.
        return self.es.transport.perform_request("POST", f"/_{ELASTIKNN_NAME}/setup")

    def create_pipeline(self, pipeline_id: str, processor_options: ProcessorOptions, description: str = None):
        proc = { ELASTIKNN_NAME: MessageToDict(processor_options) }
        req = PutPipelineRequest(description = description, processors = [proc])
        self.es.transport.perform_request("PUT", url=f"/_ingest/pipeline/{pipeline_id}", params=None, body=req.to_json())
