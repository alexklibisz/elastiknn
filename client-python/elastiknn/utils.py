
from random import Random
from typing import List, Iterator, Union

import numpy as np
from scipy.sparse import csr_matrix

from elastiknn.api import Vec

_rng = Random(0)

valid_metrics_algos = [
    ('exact', 'l1'),
    ('exact', 'l2'),
    ('exact', 'angular'),
    ('exact', 'hamming'),
    ('exact', 'jaccard'),
    ('sparse_indexed', 'jaccard'),
    ('lsh', 'jaccard')
]


def sparse_bool_vectors_to_csr(sbvs: List[Vec.SparseBool]) -> csr_matrix:
    rows, cols, data = [], [], []
    for row, sbv in enumerate(sbvs):
        for col in sbv.true_indices:
            cols.append(col)
            rows.append(row)
            data.append(True)
    return csr_matrix((data, (rows, cols)), shape=(len(sbvs), sbvs[0].total_indices), dtype=np.bool)


def csr_to_sparse_bool_vectors(csr: csr_matrix) -> Iterator[Vec.SparseBool]:
    return map(lambda row: Vec.SparseBool(true_indices=list(row.indices), total_indices=row.shape[-1]), csr)


def float_vectors_to_ndarray(fvs: List[Vec.DenseFloat]) -> np.ndarray:
    arr = np.zeros(shape=(len(fvs), len(fvs[0].values)))
    for i, fv in enumerate(fvs):
        arr[i] = list(fv.values)
    return arr


def ndarray_to_dense_float_vectors(arr: np.ndarray) -> Iterator[Vec.DenseFloat]:
    return map(lambda row: Vec.DenseFloat(values=list(row)), arr)


def ndarray_to_sparse_bool_vectors(arr: np.ndarray) -> Iterator[Vec.SparseBool]:
    return map(lambda row: Vec.SparseBool(true_indices=[i for (i, b) in enumerate(row) if b], total_indices=len(row)), arr)


def canonical_vectors_to_elastiknn(canonical: Union[np.ndarray, csr_matrix]) -> Iterator[Union[Vec.SparseBool, Vec.DenseFloat]]:
    if isinstance(canonical, np.ndarray):
        if canonical.dtype == np.bool:
            return ndarray_to_sparse_bool_vectors(canonical)
        else:
            return ndarray_to_dense_float_vectors(canonical)
    elif isinstance(canonical, csr_matrix):
        return csr_to_sparse_bool_vectors(canonical)
    else:
        raise TypeError(f"Expected a numpy array or a csr matrix but got {type(canonical)}")


def vector_length(vec: Vec.Base) -> int:
    if isinstance(vec, Vec.SparseBool):
        return vec.total_indices
    elif isinstance(vec, Vec.DenseFloat):
        return len(vec.values)
    else:
        raise TypeError
