
from random import Random
from typing import List, Iterator, Union

import numpy as np
from scipy.sparse import csr_matrix

from elastiknn.elastiknn_pb2 import SparseBoolVector, FloatVector, ElastiKnnVector

_rng = Random(0)

valid_metrics_algorithms = [
    ('l1', 'exact'),
    ('l2', 'exact'),
    ('angular', 'exact'),
    ('hamming', 'exact'),
    ('jaccard', 'exact'),
    ('jaccard', 'lsh')
]


def random_sparse_bool_vector(total_indices: int, p:float = 0.5, rng: Random = _rng):
    true_indices = [i for i in range(total_indices) if rng.random() <= p]
    return SparseBoolVector(true_indices=true_indices, total_indices=total_indices)


def sparse_bool_vectors_to_csr(sbvs: List[SparseBoolVector]) -> csr_matrix:
    rows, cols, data = [], [], []
    for row, sbv in enumerate(sbvs):
        for col in sbv.true_indices:
            cols.append(col)
            rows.append(row)
            data.append(True)
    return csr_matrix((data, (rows, cols)), shape=(len(sbvs), sbvs[0].total_indices), dtype=np.bool)


def csr_to_sparse_bool_vectors(csr: csr_matrix) -> Iterator[SparseBoolVector]:
    return map(lambda row: SparseBoolVector(true_indices=list(row.indices), total_indices=row.shape[-1]), csr)


def float_vectors_to_ndarray(fvs: List[FloatVector]) -> np.ndarray:
    arr = np.zeros(shape=(len(fvs), len(fvs[0].values)))
    for i, fv in enumerate(fvs):
        arr[i] = list(fv.values)
    return arr


def ndarray_to_float_vectors(arr: np.ndarray) -> Iterator[FloatVector]:
    return map(lambda row: FloatVector(values=list(row)), arr)


def ndarray_to_sparse_bool_vectors(arr: np.ndarray) -> Iterator[SparseBoolVector]:
    return map(lambda row: SparseBoolVector(true_indices=list(np.nonzero(row)[0]), total_indices=len(row)), arr)


def canonical_vectors_to_elastiknn(canonical: Union[np.ndarray, csr_matrix]) -> Iterator[ElastiKnnVector]:
    if isinstance(canonical, np.ndarray):
        if canonical.dtype == np.bool:
            return map(lambda sbv: ElastiKnnVector(sparse_bool_vector=sbv), ndarray_to_sparse_bool_vectors(canonical))
        else:
            return map(lambda fv: ElastiKnnVector(float_vector=fv), ndarray_to_float_vectors(canonical))
    elif isinstance(canonical, csr_matrix):
        return map(lambda sbv: ElastiKnnVector(sparse_bool_vector=sbv), csr_to_sparse_bool_vectors(canonical))
    elif isinstance(canonical, list) and isinstance(canonical[0], SparseBoolVector):
        return map(lambda sbv: ElastiKnnVector(sparse_bool_vector=sbv), canonical)
    elif isinstance(canonical, list) and isinstance(canonical[0], FloatVector):
        return map(lambda fv: ElastiKnnVector(float_vector=fv), canonical)
    elif isinstance(canonical, list) and isinstance(canonical[0], ElastiKnnVector):
        return canonical
    else:
        raise TypeError(f"Expected a numpy array or a csr matrix but got {type(canonical)}")


def elastiknn_vector_length(ekv: ElastiKnnVector) -> int:
    return max(ekv.sparse_bool_vector.total_indices, len(ekv.float_vector.values))


def default_mapping(field_raw: str) -> dict:
    return {
        "mappings": {
            "properties": {
                field_raw: {
                    "type": "elastiknn_vector"
                }
            }
        }
    }