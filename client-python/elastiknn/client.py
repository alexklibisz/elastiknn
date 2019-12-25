import json
from typing import List, Dict, Union, Iterable

import numpy as np
from dataclasses import dataclass
from dataclasses_json import dataclass_json
from elasticsearch import Elasticsearch
from elasticsearch.helpers import bulk
from google.protobuf.json_format import MessageToDict
from scipy.sparse import csr_matrix

from . import ELASTIKNN_NAME
from .elastiknn_pb2 import ProcessorOptions, ExactModelOptions, SIMILARITY_JACCARD, SparseBoolVector, FloatVector, \
    ElastiKnnVector
from .utils import ndarray_to_float_vectors, csr_to_sparse_bool_vectors


@dataclass_json
@dataclass
class PutPipelineRequest:
    description: str
    processors: List[Dict]


class ElastiKnnClient(object):

    def __init__(self, hosts: List[str] = None):
        if hosts is None:
            hosts = ["http://localhost:9200"]
        self.hosts = hosts
        self.es = Elasticsearch(self.hosts)

    def setup_cluster(self):
        # URL argument has to start with a /.
        return self.es.transport.perform_request("POST", f"/_{ELASTIKNN_NAME}/setup")

    def create_pipeline(self, pipeline_id: str, processor_options: ProcessorOptions, description: str = None):
        proc = {ELASTIKNN_NAME: MessageToDict(processor_options)}
        bod = PutPipelineRequest(description=description, processors=[proc]).to_json()
        return self.es.transport.perform_request("PUT", url=f"/_ingest/pipeline/{pipeline_id}", params=None, body=bod)

    def index(self, index: str, pipeline_id: str, field_raw: str,
              vectors: Union[Iterable[ElastiKnnVector], Iterable[SparseBoolVector], List[FloatVector], np.ndarray, csr_matrix],
              ids: List[str] = None, refresh: str = 'false', timeout: str = '30s'):
        if isinstance(vectors[0], ElastiKnnVector):
            vectors = vectors
        elif isinstance(vectors[0], SparseBoolVector):
            vectors = [ElastiKnnVector(sparse_bool_vector=v) for v in vectors]
        elif isinstance(vectors[0], FloatVector):
            vectors = [ElastiKnnVector(float_vector=v) for v in vectors]
        elif isinstance(vectors, csr_matrix):
            vectors = [ElastiKnnVector(sparse_bool_vector=v) for v in csr_to_sparse_bool_vectors(vectors)]
        elif isinstance(vectors, np.ndarray):
            vectors = [ElastiKnnVector(float_vector=v) for v in ndarray_to_float_vectors(vectors)]

        # So that the zip works.
        if ids is None or ids == []:
            ids = [None for _ in vectors]

        # bod = ""
        # action = dict(index=dict(_index=index, _type="document", pipeline=pipeline_id))
        # for vec, _id in zip(vectors, ids):
        #     if _id:
        #         action["index"]["id"] = _id
        #     elif "id" in action:
        #         del action["index"]["id"]
        #     doc = {field_raw: MessageToDict(vec)}
        #     bod += f"{json.dumps(action)}\n{json.dumps(doc)}\n"
        # params = dict(refresh=refresh, timeout=timeout)
        # return self.es.transport.perform_request("POST", "/_bulk", params=params, body=bod)

        def gen():
            d = dict(_op_type="index", _index=index, _type="document", pipeline=pipeline_id)
            for vec, _id in zip(vectors, ids):
                d["doc"] = {field_raw: MessageToDict(vec)}
                if _id:
                    d["_id"] = _id
                elif "_id" in d:
                    del d["_id"]
                yield d

        return bulk(self.es, gen())
