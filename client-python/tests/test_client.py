import pytest

from elastiknn.client import ElastiKnnClient
from elastiknn.elastiknn_pb2 import ProcessorOptions, ExactModelOptions, SIMILARITY_JACCARD
from elastiknn.utils import random_sparse_bool_vector


eknn = ElastiKnnClient()
test_index = "python-test-index"


class TestClient:

    @pytest.fixture(autouse=True)
    def before(self):
        eknn.es.indices.delete(index=test_index, ignore=[400, 404])
        eknn.es.indices.refresh()

    def test_setup(self):
        eknn.setup_cluster()
        propts = ProcessorOptions(field_raw="vec_raw", dimension=10, exact=ExactModelOptions(similarity=SIMILARITY_JACCARD))
        eknn.create_pipeline("python-test-pipeline", propts)

    def test_index(self):
        eknn.setup_cluster()
        n, dim = 1200, 128
        pipeline_id = "python-test-pipeline"
        propts = ProcessorOptions(field_raw="vec_raw", dimension=dim, exact=ExactModelOptions(similarity=SIMILARITY_JACCARD))
        eknn.create_pipeline(pipeline_id, propts)
        vecs = [random_sparse_bool_vector(dim) for _ in range(n)]
        (n_indexed, failed) = eknn.index(test_index, pipeline_id, propts.field_raw, vecs, refresh="wait_for")
        assert n_indexed == n
        assert failed == []

    def test_search_exact_jaccard(self):
        pass