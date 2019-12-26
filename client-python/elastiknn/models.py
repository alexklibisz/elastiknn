import json
import pdb
from logging import Logger
from time import time
from typing import List, Union, Tuple

import numpy as np
from scipy.sparse import csr_matrix
from sklearn.neighbors._base import NeighborsBase, KNeighborsMixin, UnsupervisedMixin

from . import ELASTIKNN_NAME
from .client import ElastiKnnClient
from .elastiknn_pb2 import ProcessorOptions, ExactModelOptions, Similarity, JaccardLshOptions
from .utils import valid_metrics_algorithms, canonical_vectors_to_elastiknn


class ElastiKnnModel(NeighborsBase, KNeighborsMixin, UnsupervisedMixin):

    def __init__(self, n_neighbors: int = None, algorithm: str = 'lsh', metric: str = 'jaccard',
                 hosts: List[str] = None, pipeline_id: str = None, index: str = None,
                 field_raw: str = "vec_raw", algorithm_params: dict = None, n_jobs: int = 4):
        if hosts is None:
            hosts = ["http://localhost:9200"]
        if (metric.lower(), algorithm.lower()) not in valid_metrics_algorithms:
            raise NotImplementedError(f"metric and algorithm must be one of: {valid_metrics_algorithms}")
        super(ElastiKnnModel, self).__init__(n_neighbors, None, 'auto', 0, metric, 0, None, n_jobs)

        self.eknn = ElastiKnnClient(hosts)
        self.metric = metric
        self.algorithm = algorithm
        self.pipeline_id = pipeline_id
        self.field_raw = field_raw
        self.algorithm_params = algorithm_params
        self.index = index
        self.n_jobs = n_jobs
        self.logger = Logger(self.__class__.__name__)

    @staticmethod
    def _check_x(X):
        if type(X) not in (np.ndarray, csr_matrix):
            raise TypeError(f"Expected X to be either a numpy ndarray or scipy csr_matrix but got {type(X)}")
        if len(X.shape) != 2:
            raise ValueError(f"Expected X to have two dimensions but got {len(X.shape)} ({X.shape})")

    def _proc_opts(self, dim: int) -> ProcessorOptions:
        sim = Similarity.Value(f"similarity_{self.metric}".upper())
        if self.algorithm == 'exact':
            return ProcessorOptions(field_raw=self.field_raw, dimension=dim, exact=ExactModelOptions(similarity=sim))
        elif self.metric == 'jaccard':
            return ProcessorOptions(field_raw=self.field_raw, dimension=dim, jaccard=JaccardLshOptions(**self.algorithm_params))
        else:
            return ProcessorOptions(field_raw=self.field_raw, dimension=dim)

    def _eknn_setup(self, X):
        dim = X.shape[-1]
        if self.pipeline_id is None:
            self.pipeline_id = f"{self.metric.lower()}-{self.algorithm.lower()}-{dim}"
            self.logger.warning(f"pipeline id was not given, using {self.pipeline_id} instead")
        if self.index is None:
            self.index = f"{ELASTIKNN_NAME}-auto-{self.pipeline_id}-{int(time())}"
            self.logger.warning(f"index was not given, using {self.index} instead")
        self.eknn.setup_cluster()
        self.eknn.create_pipeline(self.pipeline_id, self._proc_opts(dim))

    def _fit(self, X, recreate_index=False, shards: int = None, replicas: int = 0):
        self._check_x(X)
        self._eknn_setup(X)
        X = list(canonical_vectors_to_elastiknn(X))
        exists = self.eknn.es.indices.exists(self.index)
        if exists and not recreate_index:
            self.logger.warning(f"Index {self.index} already exists. Old docs might still be returned for searches.")
        elif recreate_index:
            self.logger.warning(f"Deleting and re-creating existing index {self.index}.")
            if exists:
                self.eknn.es.indices.delete(self.index)
                self.eknn.es.indices.refresh(self.index)
            if shards is None:
                shards = self.n_jobs
            self.eknn.es.indices.create(self.index, body=json.dumps({
                "settings": {
                    "number_of_shards": shards,
                    "number_of_replicas": replicas
                }
            }))
            self.eknn.es.indices.refresh(self.index)
        self.eknn.index(
            index=self.index,
            pipeline_id=self.pipeline_id,
            field_raw=self.field_raw,
            vectors=X,
            ids=[f"{i}" for i in range(len(X))])
        return self

    def kneighbors(self, X=None, n_neighbors=None, return_distance=True) \
            -> Union[Tuple[np.ndarray, np.ndarray], np.ndarray]:

        pass
