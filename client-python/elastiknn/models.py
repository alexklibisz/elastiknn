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
from .utils import canonical_vectors_to_elastiknn, valid_metrics_algos, dealias_metric


class ElastiknnModel(object):

    def __init__(self, algorithm: str, metric: str, es: Elasticsearch = None, mapping_params: dict = {},
                 query_params: dict = {}, n_jobs=1, index: str = None):
        self._logger = Logger(self.__class__.__name__)
        self._n_jobs = n_jobs
        self._tpex = ThreadPoolExecutor(self._n_jobs)
        self._vec_field = "vec"
        self._stored_id_field = "id"
        self._index = index
        self._mapping_params = mapping_params
        self._query_params = query_params
        self._eknn = ElastiknnClient(es)
        self._algorithm = algorithm
        assert (self._algorithm, metric) in valid_metrics_algos, \
            f"algorithm [{algorithm}] and metric [{metric}] should be one of {valid_metrics_algos}"
        self._metric = dealias_metric(metric)
        self._dims, self._query = None, None

    def fit(self, X: Union[np.ndarray, csr_matrix, List[Vec.SparseBool], List[Vec.DenseFloat]], shards: int = 1):
        vecs = list(canonical_vectors_to_elastiknn(X))
        self._dims = len(vecs[0])
        mapping, self._query = self._mk_mapping_query(self._query_params)

        if self._index is None:
            self._index = f"{ELASTIKNN_NAME}-{int(time())}"
            self._logger.warning(f"index was not given, using {self._index} instead")

        self._eknn.es.indices.delete(self._index, ignore=[400, 404])
        body = dict(settings=dict(number_of_shards=shards, number_of_replicas=0))
        self._eknn.es.indices.create(self._index, body=json.dumps(body))
        self._eknn.put_mapping(self._index, self._vec_field, mapping, self._stored_id_field)

        self._logger.info(f"indexing {len(vecs)} vectors into index {self._index}")
        ids = [str(i + 1) for i in range(len(vecs))]  # Add one because 0 is an invalid id in ES.
        self._eknn.index(self._index, self._vec_field, vecs, self._stored_id_field, ids, refresh=True)
        self._eknn.es.indices.forcemerge(self._index, params=dict(max_num_segments=1))
        self._eknn.index(self._index, self._vec_field, [], self._stored_id_field, [], refresh=True)


    def kneighbors(self, X: Union[np.ndarray, csr_matrix, List[Vec.SparseBool], List[Vec.DenseFloat], List[Vec.Base]],
                   n_neighbors: int, return_similarity: bool = False, progbar: bool = False, query_params: dict = None):

        if query_params is not None:
            (_, self._query) = self._mk_mapping_query(query_params)

        mapped = self._tpex.map(
            lambda v: self._eknn.nearest_neighbors(self._index, self._query.with_vec(v), self._stored_id_field,
                                                   n_neighbors, fetch_source=False),
            canonical_vectors_to_elastiknn(X))

        inds = np.ones((len(X), n_neighbors), dtype=np.int32) * -1
        sims = np.zeros((len(X), n_neighbors), dtype=np.float) * np.nan
        for i, res in tqdm(enumerate(mapped), disable=not progbar):
            hits = res['hits']['hits']
            for j, hit in enumerate(hits):
                hit['_id'] = hit['fields'][self._stored_id_field][0]    # Access stored ID.
                inds[i][j] = int(hit['_id']) - 1                        # Subtract one because 0 is an invalid id in ES.
                sims[i][j] = float(hit['_score'])

        if return_similarity:
            return inds, sims
        else:
            return inds

    def _mk_mapping_query(self, query_params: dict()) -> (Mapping.Base, NearestNeighborsQuery.Base):
        field = "vec"
        dummy = Vec.Indexed("", "", "")
        if self._algorithm == "exact":
            if self._metric == 'l1':
                return Mapping.DenseFloat(self._dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.L1)
            elif self._metric == 'l2':
                return Mapping.DenseFloat(self._dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.L2)
            elif self._metric == 'angular':
                return Mapping.DenseFloat(self._dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.Angular)
            elif self._metric == 'jaccard':
                return Mapping.SparseBool(self._dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.Jaccard)
            elif self._metric == 'hamming':
                return Mapping.SparseBool(self._dims), NearestNeighborsQuery.Exact(field, dummy, Similarity.Hamming)
        elif self._algorithm == 'sparse_indexed':
            if self._metric == 'jaccard':
                return Mapping.SparseIndexed(self._dims), NearestNeighborsQuery.SparseIndexed(field, dummy,
                                                                                        Similarity.Jaccard)
            elif self._metric == 'hamming':
                return Mapping.SparseIndexed(self._dims), NearestNeighborsQuery.SparseIndexed(field, dummy,
                                                                                        Similarity.Hamming)
        elif self._algorithm == 'lsh':
            if self._metric == 'l2':
                m, q = Mapping.L2Lsh(self._dims, **self._mapping_params), \
                       NearestNeighborsQuery.L2Lsh(field, dummy, **query_params)
                return m, q
            elif self._metric == 'angular':
                return Mapping.AngularLsh(self._dims, **self._mapping_params), \
                       NearestNeighborsQuery.AngularLsh(field, dummy, **query_params)
            elif self._metric == 'hamming':
                return Mapping.AngularLsh(self._dims, **self._mapping_params), \
                       NearestNeighborsQuery.HammingLsh(field, dummy, **query_params)
            elif self._metric == 'jaccard':
                return Mapping.JaccardLsh(self._dims, **self._mapping_params), \
                       NearestNeighborsQuery.JaccardLsh(field, dummy, **query_params)
        elif self._algorithm == 'permutation_lsh':
            if self._metric == 'angular':
                return Mapping.PermutationLsh(self._dims, **self._mapping_params), \
                       NearestNeighborsQuery.PermutationLsh(field, dummy, Similarity.Angular, **query_params)

        raise NameError