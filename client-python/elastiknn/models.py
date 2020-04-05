import json
from concurrent.futures import wait
from concurrent.futures.thread import ThreadPoolExecutor
from logging import Logger
from time import time
from typing import List, Union

import numpy as np
from scipy.sparse import csr_matrix

from . import ELASTIKNN_NAME
from .api import Mapping, Vec, NearestNeighborsQuery, Similarity
from .client import ElastiKnnClient
from .utils import canonical_vectors_to_elastiknn, valid_metrics_algos


class ElastiknnModel(object):

    def __init__(self, algorithm='exact', metric='jaccard', hosts: List[str] = None, mapping_params: dict = None,
                 query_params: dict = None, n_jobs=1, index: str = None):
        self._logger = Logger(self.__class__.__name__)
        self._hosts = hosts
        self._n_jobs = n_jobs
        self._tpex = ThreadPoolExecutor(self._n_jobs)
        self._field = "vec"
        self._index = index

        assert (algorithm, metric) in valid_metrics_algos, \
            f"algorithm and metric should be one of {valid_metrics_algos}"

        field = "vec"
        dummy = Vec.Indexed("", "", "")

        def _mk_mapping_query(dims: int) -> (Mapping.Base, NearestNeighborsQuery.Base):
            if algorithm == "exact":
                if metric == 'l1':
                    return (Mapping.DenseFloat(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.L1))
                elif metric == 'l2':
                    return (Mapping.DenseFloat(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.L2))
                elif metric == 'angular':
                    return (Mapping.DenseFloat(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.L2))
                elif metric == 'jaccard':
                    return (Mapping.SparseBool(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.Jaccard))
                elif metric == 'hamming':
                    return (Mapping.SparseBool(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.Hamming))
            elif algorithm == 'sparse_indexed':
                if metric == 'jaccard':
                    return (Mapping.SparseIndexed(dims), NearestNeighborsQuery.SparseIndexed(field, dummy, Similarity.Jaccard))
                elif metric == 'hamming':
                    return (Mapping.SparseIndexed(dims), NearestNeighborsQuery.SparseIndexed(field, dummy, Similarity.Hamming))
            elif algorithm == 'lsh':
                if metric == 'jaccard':
                    return (Mapping.JaccardLsh(dims, **mapping_params), NearestNeighborsQuery.JaccardLsh(field, dummy, **query_params))
            raise NameError

        self._mk_mapping_query = lambda dims: _mk_mapping_query(dims)
        self._mapping = None
        self._query = None

        if self._hosts is None:
            self._hosts = ["http://localhost:9200"]
            self._logger.warning(f"hosts were not given, using {self._hosts} instead")

        self._eknn = ElastiKnnClient(self._hosts)

    def fit(self, X: Union[np.ndarray, csr_matrix, List[Vec.SparseBool], List[Vec.DenseFloat]], shards: int = 1):
        vecs = list(canonical_vectors_to_elastiknn(X))
        dims = len(vecs[0])
        (self._mapping, self._query) = self._mk_mapping_query(dims)

        if self._index is None:
            self._index = f"{ELASTIKNN_NAME}-{int(time())}"
            self._logger.warning(f"index was not given, using {self._index} instead")

        self._eknn.es.indices.delete(self._index, ignore=[400, 404])
        body = dict(settings=dict(number_of_shards=shards, number_of_replicas=0))
        self._eknn.es.indices.create(self._index, body=json.dumps(body))
        self._eknn.es.indices.refresh(self._index)
        self._eknn.put_mapping(self._index, self._field, mapping=self._mapping)
        self._eknn.es.indices.refresh(self._index)

        self._logger.info(f"indexing {len(vecs)} vectors into index {self._index}")
        ids = [str(i + 1) for i in range(len(vecs))]  # Add one because 0 is an invalid id in ES.
        self._eknn.index(self._index, self._field, vecs, ids, refresh=True)

    def kneighbors(self, X: Union[np.ndarray, csr_matrix, List[Vec.SparseBool], List[Vec.DenseFloat], List[Vec.Base]],
                   n_neighbors: int, return_similarity: bool = False, allow_missing: bool = False):
        futures = []
        for vec in canonical_vectors_to_elastiknn(X):
            args = (self._index, self._query.with_vec(vec), n_neighbors, False)
            futures.append(self._tpex.submit(self._eknn.nearest_neighbors, *args))
        inds = np.ones((len(X), n_neighbors), dtype=np.int32) * -1
        sims = np.zeros((len(X), n_neighbors), dtype=np.float) * np.nan
        wait(futures)  # Ensures same order as calls.
        for i, future in enumerate(futures):
            res = future.result()
            hits = res['hits']['hits']
            assert allow_missing or len(hits) == n_neighbors, f"Expected {n_neighbors} hits for vector {i} but got {len(hits)}"
            for j, hit in enumerate(hits):
                inds[i][j] = int(hit['_id']) - 1  # Subtract one because 0 is an invalid id in ES.
                sims[i][j] = float(hit['_score'])

        if return_similarity:
            return inds, sims
        else:
            return inds
