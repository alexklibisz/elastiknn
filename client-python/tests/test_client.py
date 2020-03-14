from elastiknn.client import ElastiKnnClient
from elastiknn.elastiknn_pb2 import *
from elastiknn.utils import random_sparse_bool_vector

eknn = ElastiKnnClient()
field_raw = "vec_raw"


class TestClient:

    def test_search_exact_jaccard(self):
        test_index = pipeline_id = "python-test-exact-jaccard"
        n, dim = 1200, 128
        propts = ProcessorOptions(field_raw=field_raw, dimension=dim,
                                  exact_computed=ExactComputedModelOptions(similarity=SIMILARITY_JACCARD))
        eknn.create_pipeline(pipeline_id, propts)
        eknn.es.indices.delete(index=test_index, ignore=[400, 404])
        eknn.es.indices.refresh()
        eknn.es.indices.create(test_index)
        eknn.es.indices.refresh()
        eknn.prepare_mapping(test_index, propts)
        vecs = [random_sparse_bool_vector(dim) for _ in range(n)]
        (_, _) = eknn.index(test_index, pipeline_id, propts.field_raw, vecs)
        qopts = ExactComputedQueryOptions()
        qvec = ElastiKnnVector(sparse_bool_vector=random_sparse_bool_vector(dim))
        res = eknn.knn_query(test_index, pipeline_id, qopts, qvec)
        assert len(res["hits"]["hits"]) == 10

    def test_search_lsh_jaccard(self):
        test_index = pipeline_id = "python-test-lsh-jaccard"
        n, dim = 1200, 128
        propts = ProcessorOptions(field_raw=field_raw, dimension=dim,
                                  jaccard_lsh=JaccardLshModelOptions(field_processed="vec_proc", num_bands=10, num_rows=2))
        eknn.create_pipeline(pipeline_id, propts)
        eknn.es.indices.delete(index=test_index, ignore=[400, 404])
        eknn.es.indices.refresh()
        eknn.es.indices.create(test_index)
        eknn.es.indices.refresh()
        eknn.prepare_mapping(test_index, propts)
        vecs = [random_sparse_bool_vector(dim) for _ in range(n)]
        (_, _) = eknn.index(test_index, pipeline_id, propts.field_raw, vecs)
        qopts = JaccardLshQueryOptions(num_candidates=10)
        qvec = ElastiKnnVector(sparse_bool_vector=random_sparse_bool_vector(dim))
        res = eknn.knn_query(test_index, pipeline_id, qopts, qvec, n_neighbors=10)
        assert len(res["hits"]["hits"]) == 10
