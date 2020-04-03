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
from .utils import canonical_vectors_to_elastiknn, valid_metrics_algos, vector_length


# class ElastiKnnModel(NeighborsBase, KNeighborsMixin):
#
#     def __init__(self, n_neighbors: int = None, algorithm: str = 'exact', metric: str = 'jaccard',
#                  hosts: List[str] = None, pipeline_id: str = None, index: str = None,
#                  field_raw: str = "vec_raw", algorithm_params: dict = None, n_jobs: int = 4):
#         if hosts is None:
#             hosts = ["http://localhost:9200"]
#         if (metric.lower(), algorithm.lower()) not in valid_metrics_algorithms:
#             raise NotImplementedError(f"metric and algorithm must be one of: {valid_metrics_algorithms}")
#         super(ElastiKnnModel, self).__init__(n_neighbors, None, 'auto', 0, metric, 0, None, n_jobs)
#
#         self._eknn = ElastiKnnClient(hosts)
#         self._sim = Similarity.Value(f"similarity_{metric}".upper())
#         self._algorithm = algorithm
#         self._pipeline_id = pipeline_id
#         self._field_raw = field_raw
#         self._algorithm_params = algorithm_params
#         self._index = index
#         self._n_jobs = n_jobs
#         self._tpex = ThreadPoolExecutor(self._n_jobs)
#         self._field_proc = "vec_proc"
#         self._dataset_index_key = "dataset_index"
#         self._logger = Logger(self.__class__.__name__)
#
#     def _proc_opts(self, dim: int) -> ProcessorOptions:
#         if self._algorithm == 'exact':
#             return ProcessorOptions(field_raw=self._field_raw, dimension=dim,
#                                     exact_computed=ExactComputedModelOptions(similarity=self._sim))
#         elif (self._algorithm, self._sim) == ('indexed', SIMILARITY_JACCARD):
#             return ProcessorOptions(field_raw=self._field_raw, dimension=dim,
#                                     jaccard_indexed=JaccardIndexedModelOptions(
#                                         field_processed=self._field_proc))
#         elif (self._algorithm, self._sim) == ('lsh', SIMILARITY_JACCARD):
#             return ProcessorOptions(field_raw=self._field_raw, dimension=dim,
#                                     jaccard_lsh=JaccardLshModelOptions(
#                                         field_processed=self._field_proc, **self._algorithm_params))
#         else:
#             raise RuntimeError(f"Couldn't determine valid processor options")
#
#     def _query_opts(self, n_neighbors: int):
#         if self._algorithm == 'exact':
#             return ExactComputedQueryOptions()
#         elif (self._algorithm, self._sim) == ('indexed', SIMILARITY_JACCARD):
#             return JaccardIndexedQueryOptions()
#         elif (self._algorithm, self._sim) == ('lsh', SIMILARITY_JACCARD):
#             return JaccardLshQueryOptions(num_candidates=n_neighbors)
#         else:
#             raise RuntimeError(f"Couldn't determine valid query options")
#
#     def fit(self, X: Union[np.ndarray, csr_matrix, List[ElastiKnnVector], List[SparseBoolVector], List[FloatVector]],
#             y=None, recreate_index=True, shards: int = None, replicas: int = 0):
#         if y is not None:
#             self._logger.warning(f"y was given but will be ignored")
#         X = list(canonical_vectors_to_elastiknn(X))
#         dim = elastiknn_vector_length(X[0])
#         proc_opts = self._proc_opts(dim)
#         if self._pipeline_id is None:
#             self._pipeline_id = f"{Similarity.Name(self._sim).lower()}-{self._algorithm.lower()}-{int(time())}"
#             self._logger.warning(f"pipeline id was not given, using {self._pipeline_id} instead")
#         if self._index is None:
#             self._index = f"{ELASTIKNN_NAME}-auto-{self._pipeline_id}-{int(time())}"
#             self._logger.warning(f"index was not given, using {self._index} instead")
#         self._eknn.create_pipeline(self._pipeline_id, proc_opts)
#         docs = [{self._dataset_index_key: i} for i in range(len(X))]
#         exists = self._eknn.es.indices.exists(self._index)
#         if exists and not recreate_index:
#             raise RuntimeError(f"Index {self._index} already exists but recreate_index was set to False.")
#         elif recreate_index:
#             self._logger.warning(f"Deleting and re-creating existing index {self._index}.")
#             if exists:
#                 self._eknn.es.indices.delete(self._index)
#             if shards is None:
#                 shards = self._n_jobs
#             body = dict(
#                 settings=dict(
#                     number_of_shards=shards,
#                     number_of_replicas=replicas
#                 )
#             )
#             self._eknn.es.indices.create(self._index, body=json.dumps(body))
#             self._eknn.es.indices.refresh(self._index)
#         self._eknn.prepare_mapping(self._index, proc_opts)
#         self._eknn.index(
#             index=self._index,
#             pipeline_id=self._pipeline_id,
#             field_raw=self._field_raw,
#             vectors=X,
#             docs=docs)
#         return self
#
#     def kneighbors(self, X: Union[np.ndarray, csr_matrix, List[ElastiKnnVector], List[SparseBoolVector], List[FloatVector]] = None,
#                    n_neighbors: int = None, return_distance: bool = True, use_cache: bool = False, allow_missing: bool = False) -> Union[Tuple[np.ndarray, np.ndarray], np.ndarray]:
#         X = list(canonical_vectors_to_elastiknn(X))
#         if n_neighbors is None:
#             n_neighbors = self.n_neighbors
#         qopts = self._query_opts(n_neighbors)
#         futures = []
#         for x in X:
#             futures.append(self._tpex.submit(self._eknn.knn_query, self._index, self._pipeline_id, qopts, x,
#                                              n_neighbors, [self._dataset_index_key], use_cache))
#         indices = np.ones((len(X), n_neighbors), dtype=np.uint32) * -1
#         dists = np.zeros((len(X), n_neighbors), dtype=np.float) * np.nan
#         wait(futures)   # To ensure same order.
#         for i, future in enumerate(futures):
#             res = future.result()
#             hits = res['hits']['hits']
#             assert allow_missing or len(hits) == n_neighbors, f"Expected to get {n_neighbors} hits for vector {i} but got {len(hits)}."
#             for j, hit in enumerate(hits):
#                 indices[i][j] = hit['_source'][self._dataset_index_key]
#                 dists[i][j] = hit['_score']
#         if return_distance:
#             return np.array(indices), np.array(dists)
#         else:
#             return np.array(indices)


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
        dims = vector_length(vecs[0])
        (self._mapping, self._query) = self._mk_mapping_query(dims)

        if self._index is None:
            self._index = f"{ELASTIKNN_NAME}-{int(time())}"
            self._logger.warning(f"index was not given, using {self._index} instead")

        self._eknn.es.indices.delete(self._index, ignore=[400, 404])
        body = dict(settings=dict(number_of_shards=shards, number_of_replicas=0))
        self._eknn.es.indices.create(self._index, body=json.dumps(body))
        self._eknn.es.indices.refresh(self._index)
        self._eknn.put_mapping(self._index, self._field, mapping=self._mapping)

        self._logger.info(f"indexing {len(vecs)} vectors into index {self._index}")
        ids = [str(i + 1) for i in range(len(vecs))]  # Add one because 0 is an invalid id in ES.
        self._eknn.index(self._index, self._field, vecs, ids, refresh=True)

    def kneighbors(self, X: Union[np.ndarray, csr_matrix, List[Vec.SparseBool], List[Vec.DenseFloat]],
                   n_neighbors: int = None, return_similarity: bool = False, allow_missing: bool = False):
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
