from elastiknn.client import ElastiKnnClient
from elastiknn.elastiknn_pb2 import ProcessorOptions, ExactModelOptions, SIMILARITY_JACCARD


class TestClient:

    def test_setup(self):
        eknn = ElastiKnnClient()
        eknn.setup_cluster()
        propts = ProcessorOptions(field_raw="vec_raw", dimension=10, exact = ExactModelOptions(similarity=SIMILARITY_JACCARD))
        eknn.create_pipeline("python-test-pipeline", propts)

    def test_index(self):
        pass

    def test_search_exact_jaccard(self):
        pass