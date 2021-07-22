import numpy as np
from sklearn.datasets import load_digits
from sklearn.neighbors import NearestNeighbors

from elastiknn.models import ElastiknnModel

digits = load_digits(n_class=10)["data"] > 0
digits_train = digits[:1400]
digits_validate = digits[1400:]


class TestModel:

    @staticmethod
    def recall(A: np.ndarray, B: np.ndarray) -> np.ndarray:
        return np.array([
            len(set(a).intersection(set(b))) / A.shape[-1]
            for a, b in zip(A, B)
        ])

    def test_exact_jaccard_mnist(self):
        # First run the query and make sure the results have the right form.
        n_neighbors = 20
        model = ElastiknnModel('exact', 'jaccard')
        model.fit(digits_train)

        inds1 = model.kneighbors(digits_validate, n_neighbors)
        inds2, dists2 = model.kneighbors(digits_validate, n_neighbors, return_similarity=True)

        assert np.all(inds1 == inds2)
        assert inds1.shape == (digits_validate.shape[0], n_neighbors)
        assert dists2.shape == inds2.shape

        # Then compare against scikit-learn. Intentionally using fewer neighbors to make sure recall will be 1
        # despite out-of-order indices due to equal distances.
        ref = NearestNeighbors(n_neighbors=int(n_neighbors / 2), algorithm='brute', metric='jaccard', n_jobs=1)
        ref.fit(digits_train)
        inds3 = ref.kneighbors(digits_validate, return_distance=False)

        # Compute and check the recall.
        rec = self.recall(inds3, inds2)
        assert np.all(rec == 1)

    def test_lsh_jaccard_random_data(self):
        pass