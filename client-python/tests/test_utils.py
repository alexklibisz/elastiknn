from random import Random

from elastiknn.utils import random_sparse_bool_vector, sparse_bool_vectors_to_csr, csr_to_sparse_bool_vectors


class TestUtils:

    def test_random_sparse_bool_vector(self):
        rng = Random(22)
        sbvs = [random_sparse_bool_vector(20, 0.5, rng) for _ in range(50)]
        for sbv in sbvs:
            assert len(sbv.true_indices) > 0
            assert len(sbv.true_indices) < 20
            assert sbv.total_indices == 20

    def test_sparse_bool_vector_to_csr(self):
        sbvs = [random_sparse_bool_vector(128, 0.5) for _ in range(50)]
        csr = sparse_bool_vectors_to_csr(sbvs)
        assert csr.shape == (50, 128)
        sbvs2 = csr_to_sparse_bool_vectors(csr)
        assert sbvs2 == sbvs
