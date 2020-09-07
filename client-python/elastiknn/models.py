import json
from concurrent.futures import wait
from concurrent.futures.thread import ThreadPoolExecutor
from elasticsearch import Elasticsearch
from logging import Logger
from time import time
from typing import List, Union

import numpy as np
from scipy.sparse import csr_matrix
from tqdm import tqdm

from . import ELASTIKNN_NAME
from .api import Mapping, Vec, NearestNeighborsQuery, Similarity
from .client import ElastiknnClient
from .utils import canonical_vectors_to_elastiknn, valid_metrics_algos


class ElastiknnModel(object):

    def __init__(self, algorithm: str, metric: str, es: Elasticsearch = None, mapping_params: dict = None,
                 query_params: dict = None, n_jobs=1, index: str = None):
        self._logger = Logger(self.__class__.__name__)
        self._n_jobs = n_jobs
        self._tpex = ThreadPoolExecutor(self._n_jobs)
        self._vec_field = "vec"
        self._stored_id_field = "id"
        self._index = index

        assert (algorithm, metric) in valid_metrics_algos, \
            f"algorithm and metric should be one of {valid_metrics_algos}"

        field = "vec"
        dummy = Vec.Indexed("", "", "")

        # Function combining in-scope variables and a later-provided dims to generate a mapping and query.
        def _mk_mapping_query(dims: int) -> (Mapping.Base, NearestNeighborsQuery.Base):
            if algorithm == "exact":
                if metric == 'l1':
                    return Mapping.DenseFloat(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.L1)
                elif metric == 'l2':
                    return Mapping.DenseFloat(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.L2)
                elif metric == 'angular':
                    return Mapping.DenseFloat(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.Angular)
                elif metric == 'jaccard':
                    return Mapping.SparseBool(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.Jaccard)
                elif metric == 'hamming':
                    return Mapping.SparseBool(dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.Hamming)
            elif algorithm == 'sparse_indexed':
                if metric == 'jaccard':
                    return Mapping.SparseIndexed(dims), NearestNeighborsQuery.SparseIndexed(field, dummy, Similarity.Jaccard)
                elif metric == 'hamming':
                    return Mapping.SparseIndexed(dims), NearestNeighborsQuery.SparseIndexed(field, dummy, Similarity.Hamming)
            elif algorithm == 'lsh':
                if metric == 'l2':
                    return Mapping.L2LSH(dims, **mapping_params), \
                           NearestNeighborsQuery.L2LSH(field, dummy, Similarity.L2, **query_params)
                elif metric == 'angular':
                    return Mapping.AngularLsh(dims, **mapping_params), \
                           NearestNeighborsQuery.AngularLsh(field, dummy, Similarity.Angular, **query_params)
                elif metric == 'hamming':
                    return Mapping.AngularLsh(dims, **mapping_params), \
                           NearestNeighborsQuery.HammingLsh(field, dummy, Similarity.Hamming, **query_params)
                elif metric == 'jaccard':
                    return Mapping.JaccardLsh(dims, **mapping_params), \
                           NearestNeighborsQuery.JaccardLsh(field, dummy, **query_params)
            raise NameError

        self._mk_mapping_query = lambda dims: _mk_mapping_query(dims)
        self._mapping = None
        self._query = None
        self._eknn = ElastiknnClient(es)

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
        self._eknn.put_mapping(self._index, self._vec_field, self._mapping, self._stored_id_field)

        self._logger.info(f"indexing {len(vecs)} vectors into index {self._index}")
        ids = [str(i + 1) for i in range(len(vecs))]  # Add one because 0 is an invalid id in ES.
        self._eknn.index(self._index, self._vec_field, vecs, self._stored_id_field, ids, refresh=True)
        self._eknn.es.indices.forcemerge(self._index, params=dict(max_num_segments=1))


    def kneighbors(self, X: Union[np.ndarray, csr_matrix, List[Vec.SparseBool], List[Vec.DenseFloat], List[Vec.Base]],
                   n_neighbors: int, return_similarity: bool = False, allow_missing: bool = False, progbar: bool = False):
        mapped = self._tpex.map(
            lambda v: self._eknn.nearest_neighbors(self._index, self._query.with_vec(v), self._stored_id_field,
                                                   n_neighbors, False),
            canonical_vectors_to_elastiknn(X))
        inds = np.ones((len(X), n_neighbors), dtype=np.int32) * -1
        sims = np.zeros((len(X), n_neighbors), dtype=np.float) * np.nan
        for i, res in tqdm(enumerate(mapped), disable=not progbar):
            hits = res['hits']['hits']
            assert allow_missing or len(hits) == n_neighbors, f"Expected {n_neighbors} hits for vector {i} but got {len(hits)}"
            for j, hit in enumerate(hits):
                inds[i][j] = int(hit['_id']) - 1    # Subtract one because 0 is an invalid id in ES.
                sims[i][j] = float(hit['_score'])

        if return_similarity:
            return inds, sims
        else:
            return inds
