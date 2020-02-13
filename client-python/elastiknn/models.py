import json
from concurrent.futures import wait
from concurrent.futures.thread import ThreadPoolExecutor
from logging import Logger
from time import time
from typing import List, Union, Tuple

import numpy as np
from scipy.sparse import csr_matrix
from sklearn.neighbors._base import NeighborsBase, KNeighborsMixin

from . import ELASTIKNN_NAME
from .client import ElastiKnnClient
from .elastiknn_pb2 import ProcessorOptions, ExactModelOptions, Similarity, JaccardLshOptions, KNearestNeighborsQuery, \
    SIMILARITY_JACCARD, ElastiKnnVector, SparseBoolVector, FloatVector
from .utils import valid_metrics_algorithms, canonical_vectors_to_elastiknn, default_mapping, elastiknn_vector_length


class ElastiKnnModel(NeighborsBase, KNeighborsMixin):

    def __init__(self, n_neighbors: int = None, algorithm: str = 'lsh', metric: str = 'jaccard',
                 hosts: List[str] = None, pipeline_id: str = None, index: str = None,
                 field_raw: str = "vec_raw", algorithm_params: dict = None, n_jobs: int = 4):
        if hosts is None:
            hosts = ["http://localhost:9200"]
        if (metric.lower(), algorithm.lower()) not in valid_metrics_algorithms:
            raise NotImplementedError(f"metric and algorithm must be one of: {valid_metrics_algorithms}")
        super(ElastiKnnModel, self).__init__(n_neighbors, None, 'auto', 0, metric, 0, None, n_jobs)

        self._eknn = ElastiKnnClient(hosts)
        self._sim = Similarity.Value(f"similarity_{metric}".upper())
        self._algorithm = algorithm
        self._pipeline_id = pipeline_id
        self._field_raw = field_raw
        self._algorithm_params = algorithm_params
        self._index = index
        self._n_jobs = n_jobs
        self._tpex = ThreadPoolExecutor(self._n_jobs)
        self._field_proc = "vec_proc"
        self._dataset_index_key = "dataset_index"
        self._logger = Logger(self.__class__.__name__)

    def _proc_opts(self, dim: int) -> ProcessorOptions:
        if self._algorithm == 'exact':
            return ProcessorOptions(field_raw=self._field_raw, dimension=dim, exact=ExactModelOptions(similarity=self._sim))
        elif self._sim == SIMILARITY_JACCARD:
            return ProcessorOptions(field_raw=self._field_raw, dimension=dim,
                                    jaccard=JaccardLshOptions(field_processed=self._field_proc, **self._algorithm_params))
        else:
            raise RuntimeError(f"Couldn't determine valid processor options")

    def _query_opts(self) -> Union[KNearestNeighborsQuery.ExactQueryOptions, KNearestNeighborsQuery.LshQueryOptions]:
        if self._algorithm == 'exact':
            return KNearestNeighborsQuery.ExactQueryOptions(field_raw=self._field_raw, similarity=self._sim)
        elif self._sim == SIMILARITY_JACCARD:
            return KNearestNeighborsQuery.LshQueryOptions(pipeline_id=self._pipeline_id)
        else:
            raise RuntimeError(f"Couldn't determine valid query options")

    def _eknn_setup(self, X: List[ElastiKnnVector]):
        dim = elastiknn_vector_length(X[0])
        if self._pipeline_id is None:
            self._pipeline_id = f"{Similarity.Name(self._sim).lower()}-{self._algorithm.lower()}-{dim}"
            self._logger.warning(f"pipeline id was not given, using {self._pipeline_id} instead")
        if self._index is None:
            self._index = f"{ELASTIKNN_NAME}-auto-{self._pipeline_id}-{int(time())}"
            self._logger.warning(f"index was not given, using {self._index} instead")
        self._eknn.create_pipeline(self._pipeline_id, self._proc_opts(dim))

    def fit(self, X: Union[np.ndarray, csr_matrix, List[ElastiKnnVector], List[SparseBoolVector], List[FloatVector]],
            y=None, recreate_index=True, shards: int = None, replicas: int = 0):
        if y is not None:
            self._logger.warning(f"y was given but will be ignored")
        X = list(canonical_vectors_to_elastiknn(X))
        self._eknn_setup(X)
        docs = [{self._dataset_index_key: i} for i in range(len(X))]
        exists = self._eknn.es.indices.exists(self._index)
        if exists and not recreate_index:
            raise RuntimeError(f"Index {self._index} already exists but recreate_index was set to False.")
        elif recreate_index:
            self._logger.warning(f"Deleting and re-creating existing index {self._index}.")
            if exists:
                self._eknn.es.indices.delete(self._index)
                self._eknn.es.indices.refresh(self._index)
            if shards is None:
                shards = self._n_jobs
            body = dict(
                settings=dict(
                    number_of_shards=shards,
                    number_of_replicas=replicas
                ),
                **default_mapping(self._field_raw)
            )
            self._eknn.es.indices.create(self._index, body=json.dumps(body))
            self._eknn.es.indices.refresh(self._index)
        self._eknn.index(
            index=self._index,
            pipeline_id=self._pipeline_id,
            field_raw=self._field_raw,
            vectors=X,
            docs=docs)
        return self

    def kneighbors(self, X: Union[np.ndarray, csr_matrix, List[ElastiKnnVector], List[SparseBoolVector], List[FloatVector]] = None,
                   n_neighbors: int = None, return_distance: bool = True, use_cache: bool = False) -> Union[Tuple[np.ndarray, np.ndarray], np.ndarray]:
        X = list(canonical_vectors_to_elastiknn(X))
        if n_neighbors is None:
            n_neighbors = self.n_neighbors
        qopts = self._query_opts()
        futures = []
        for x in X:
            futures.append(self._tpex.submit(self._eknn.knn_query, self._index, qopts, x, n_neighbors, [self._dataset_index_key], use_cache))
        indices, dists = np.zeros((len(X), n_neighbors), dtype=np.uint32), np.zeros((len(X), n_neighbors), dtype=np.float)
        wait(futures) # To ensure same order.
        for i, future in enumerate(futures):
            res = future.result()
            hits = res['hits']['hits']
            assert len(hits) == n_neighbors, f"Expected to get {n_neighbors} hits for vector {i} but got {len(hits)}."
            for j, hit in enumerate(hits):
                indices[i][j] = hit['_source'][self._dataset_index_key]
                dists[i][j] = hit['_score']
        if return_distance:
            return np.array(indices), np.array(dists)
        else:
            return np.array(indices)
