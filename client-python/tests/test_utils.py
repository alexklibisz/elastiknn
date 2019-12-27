from random import Random

import numpy as np

from elastiknn.elastiknn_pb2 import SparseBoolVector, FloatVector
from elastiknn.utils import random_sparse_bool_vector, sparse_bool_vectors_to_csr, csr_to_sparse_bool_vectors, \
    float_vectors_to_ndarray, ndarray_to_float_vectors, ndarray_to_sparse_bool_vectors


class TestUtils:

    def test_random_sparse_bool_vector(self):
        rng = Random(22)
        sbvs = [random_sparse_bool_vector(20, 0.5, rng) for _ in range(50)]
        for sbv in sbvs:
            assert len(sbv.true_indices) > 0
            assert len(sbv.true_indices) < 20
            assert sbv.total_indices == 20

    def test_sparse_bool_vector_conversion(self):
        sbvs = [
            SparseBoolVector(true_indices=[0, 2, 3], total_indices=8),
            SparseBoolVector(true_indices=[1, 4, 7], total_indices=8),
            SparseBoolVector(true_indices=[0, 1], total_indices=8)
        ]
        csr = sparse_bool_vectors_to_csr(sbvs)
        cmp = np.array([
            [i in sbv.true_indices for i in range(8)]
            for sbv in sbvs
        ])
        assert np.all(csr.toarray() == cmp)
        assert csr.shape == (3, 8)
        sbvs2 = csr_to_sparse_bool_vectors(csr)
        assert list(sbvs2) == sbvs
        sbvs3 = ndarray_to_sparse_bool_vectors(csr.toarray())
        assert list(sbvs3) == sbvs

    def test_float_vector_conversion(self):
        fvs = [FloatVector(values=row) for row in [
            [0.0, 1.0, 2.0],
            [2.0, 2.5, 3.0],
            [3.0, 3.3, 3.7]
        ]]
        arr = float_vectors_to_ndarray(fvs)
        cmp = np.array([v.values for v in fvs])
        assert np.all(arr == cmp)
        fvs2 = list(ndarray_to_float_vectors(arr))
        assert fvs2 == fvs


