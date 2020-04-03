from elastiknn.utils import *


class TestUtils:

    def test_random_sparse_bool_vector(self):
        rng = Random(22)
        sbvs = [Vec.SparseBool.random(20, rng) for _ in range(50)]
        for sbv in sbvs:
            assert len(sbv.true_indices) > 0
            assert len(sbv.true_indices) < 20
            assert sbv.total_indices == 20

    def test_sparse_bool_vector_conversion(self):
        sbvs = [
            Vec.SparseBool(true_indices=[0, 2, 3], total_indices=8),
            Vec.SparseBool(true_indices=[1, 4, 7], total_indices=8),
            Vec.SparseBool(true_indices=[0, 1], total_indices=8)
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
        fvs = [Vec.DenseFloat(values=row) for row in [
            [0.0, 1.0, 2.0],
            [2.0, 2.5, 3.0],
            [3.0, 3.3, 3.7]
        ]]
        arr = float_vectors_to_ndarray(fvs)
        cmp = np.array([v.values for v in fvs])
        assert np.all(arr == cmp)
        fvs2 = list(ndarray_to_dense_float_vectors(arr))
        assert fvs2 == fvs


