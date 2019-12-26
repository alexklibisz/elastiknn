from logging import Logger
from typing import List, Union, Tuple

import numpy as np
from scipy.sparse import csr_matrix
from sklearn.base import BaseEstimator
from sklearn.neighbors._base import NeighborsBase, KNeighborsMixin, UnsupervisedMixin
from sklearn.utils.validation import check_is_fitted
from time import time

from .client import ElastiKnnClient
from . import ELASTIKNN_NAME
from .elastiknn_pb2 import ProcessorOptions, ExactModelOptions, Similarity, JaccardLshOptions


class ElastiKnnModel(NeighborsBase, KNeighborsMixin, UnsupervisedMixin):
    _valid_metrics_algorithms = [
        ('l1', 'brute'),
        ('l2', 'brute'),
        ('angular', 'brute'),
        ('hamming', 'brute'),
        ('jaccard', 'brute'),
        ('jaccard', 'lsh')
    ]

    def __init__(self, n_neighbors: int = None, algorithm: str = 'lsh', metric: str = 'jaccard',
                 hosts: List[str] = None, pipeline_id: str = None, index: str = None,
                 field_raw: str = "vec_raw", algorithm_params: dict = None):
        if hosts is None:
            hosts = ["http://localhost:9200"]
        if (metric.lower(), algorithm.lower()) not in ElastiKnnModel._valid_metrics_algorithms:
            raise NotImplementedError(f"metric and algorithm must be one of: {ElastiKnnModel._valid_metrics_algorithms}")
        super(ElastiKnnModel, self).__init__(n_neighbors, None, 'auto', 0, metric, 0, None, None)

        self.eknn = ElastiKnnClient(hosts)
        self.metric = metric
        self.algorithm = algorithm
        self.pipeline_id = pipeline_id
        self.field_raw = field_raw
        self.algorithm_params = algorithm_params
        self.index = index
        self.logger = Logger(self.__class__.__name__)

    def _check_X(self, X):
        if type(X) not in (np.ndarray, csr_matrix):
            raise TypeError(f"Expected X to be either a numpy ndarray or scipy csr_matrix but got {type(X)}")
        if len(X.shape) != 2:
            raise ValueError(f"Expected X to have two dimensions but got {len(X.shape)} ({X.shape})")

    def _proc_opts(self, dim: int) -> ProcessorOptions:
        sim = Similarity.Value(f"similarity_{self.algorithm}".upper())
        popts = ProcessorOptions(field_raw=self.field_raw, dimension=dim)
        if self.algorithm == 'brute':
            popts.exact = ExactModelOptions(similarity=sim)
        elif self.metric == 'jaccard':
            popts.jaccard = JaccardLshOptions(**self.algorithm_params)
        return popts

    def _eknn_setup(self, X):
        dim = X.shape[-1]
        if self.pipeline_id is None:
            self.pipeline_id = f"{self.metric.lower()}-{self.algorithm.lower()}-{dim}"
            self.logger.warning(f"pipeline id was not given, using {self.pipeline_id} instead")
        if self.index is not None:
            self.index = f"{ELASTIKNN_NAME}-auto-{self.pipeline_id}-{int(time())}"
            self.logger.warning(f"index was not given, using {self.index} instead")
        self.eknn.setup_cluster()
        self.eknn.create_pipeline(self.pipeline_id, self._proc_opts(dim))

    def _fit(self, X):
        self._check_X(X)
        self._eknn_setup(X)
        self.eknn.index(
            index=self.index,
            pipeline_id=self.pipeline_id,
            field_raw=self.field_raw,
            vectors=X)
        return self

    def kneighbors(self, X=None, n_neighbors=None, return_distance=True) \
            -> Union[Tuple[np.ndarray, np.ndarray], np.ndarray]:
        self.eknn

        pass
