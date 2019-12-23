
from random import Random
from typing import List

from scipy.sparse import csr_matrix

from elastiknn.elastiknn_pb2 import SparseBoolVector

_rng = Random(0)


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
    return csr_matrix((data, (rows, cols)), shape=(len(sbvs), sbvs[0].total_indices))


def csr_to_sparse_bool_vectors(csr: csr_matrix) -> List[SparseBoolVector]:
    return [
        SparseBoolVector(true_indices=list(row.indices), total_indices=row.shape[-1])
        for row in csr
    ]