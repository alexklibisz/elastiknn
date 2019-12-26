from scipy.sparse import csr_matrix
from sklearn.datasets import load_digits

from elastiknn.models import ElastiKnnModel

digits = csr_matrix(load_digits(10)["data"] > 0)


class TestModel:

    def test_exact_jaccard_mnist(self):
        model = ElastiKnnModel(20, algorithm='exact', metric='jaccard')
        model.fit(digits)
        pass

    def test_lsh_jaccard_random_data(self):
        pass