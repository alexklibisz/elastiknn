import json

from elastiknn.client import ElastiKnnClient
from elastiknn.elastiknn_pb2 import *
from elastiknn.utils import random_sparse_bool_vector, default_mapping

eknn = ElastiKnnClient()
field_raw = "vec_raw"


class TestClient:

    def test_setup_cluster(self):
        eknn.setup_cluster()

    def test_search_exact_jaccard(self):
        test_index = pipeline_id = "python-test-exact-jaccard"
        n, dim = 1200, 128
        propts = ProcessorOptions(field_raw=field_raw, dimension=dim,
                                  exact=ExactModelOptions(similarity=SIMILARITY_JACCARD))
        eknn.create_pipeline(pipeline_id, propts)
        eknn.es.indices.delete(index=test_index, ignore=[400, 404])
        eknn.es.indices.refresh()
        eknn.es.indices.create(test_index, body=json.dumps(default_mapping(field_raw)))
        vecs = [random_sparse_bool_vector(dim) for _ in range(n)]
        (_, _) = eknn.index(test_index, pipeline_id, propts.field_raw, vecs)
        qopts = KNearestNeighborsQuery.ExactQueryOptions(field_raw=field_raw, similarity=SIMILARITY_JACCARD)
        qvec = ElastiKnnVector(sparse_bool_vector=random_sparse_bool_vector(dim))
        res = eknn.knn_query(test_index, qopts, qvec)
        assert len(res["hits"]["hits"]) == 10

    def test_search_lsh_jaccard(self):
        test_index = pipeline_id = "python-test-lsh-jaccard"
        n, dim = 1200, 128
        propts = ProcessorOptions(field_raw=field_raw, dimension=dim,
                                  jaccard=JaccardLshOptions(field_processed="vec_proc", num_tables=5, num_bands=10,
                                                            num_rows=2))
        eknn.create_pipeline(pipeline_id, propts)
        eknn.es.indices.delete(index=test_index, ignore=[400, 404])
        eknn.es.indices.refresh()
        eknn.es.indices.create(test_index, body=json.dumps(default_mapping(field_raw)))
        vecs = [random_sparse_bool_vector(dim) for _ in range(n)]
        (_, _) = eknn.index(test_index, pipeline_id, propts.field_raw, vecs)
        qopts = KNearestNeighborsQuery.LshQueryOptions(pipeline_id=pipeline_id)
        qvec = ElastiKnnVector(sparse_bool_vector=random_sparse_bool_vector(dim))
        res = eknn.knn_query(test_index, qopts, qvec)
        assert len(res["hits"]["hits"]) == 10
