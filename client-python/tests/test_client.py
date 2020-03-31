from elastiknn.api import Mapping, Vec, NearestNeighborsQuery, Similarity
from elastiknn.client import ElastiKnnClient


class TestClient:

    def test_exact_jaccard(self):
        eknn = ElastiKnnClient()
        n, dim = 1200, 128
        index = "py-test-exact-jaccard"
        field = "vec"
        mapping = Mapping.SparseBool(dims=dim)

        eknn.es.indices.delete(index, ignore=[400, 404])
        eknn.es.indices.refresh()
        eknn.es.indices.create(index)
        eknn.es.indices.refresh()
        eknn.put_mapping(index, field, mapping)

        vecs = [Vec.SparseBool.random(dim) for _ in range(n)]
        ids = list(range(n))
        (n_, errors) = eknn.index(index, field, vecs, ids, refresh=True)

        qvec = vecs[0]
        query = NearestNeighborsQuery.Exact(field, qvec, Similarity.Jaccard)
        res = eknn.nearest_neighbors(index, query, 11, False)

        assert n_ == n
        assert len(errors) == 0



    # def test_search_exact_jaccard(self):
    #     test_index = pipeline_id = "python-test-exact-jaccard"
    #     n, dim = 1200, 128
    #     propts = ProcessorOptions(field_raw=field_raw, dimension=dim,
    #                               exact_computed=ExactComputedModelOptions(similarity=SIMILARITY_JACCARD))
    #     eknn.create_pipeline(pipeline_id, propts)
    #     eknn.es.indices.delete(index=test_index, ignore=[400, 404])
    #     eknn.es.indices.refresh()
    #     eknn.es.indices.create(test_index)
    #     eknn.es.indices.refresh()
    #     eknn.prepare_mapping(test_index, propts)
    #     vecs = [random_sparse_bool_vector(dim) for _ in range(n)]
    #     (_, _) = eknn.index(test_index, pipeline_id, propts.field_raw, vecs)
    #     qopts = ExactComputedQueryOptions()
    #     qvec = ElastiKnnVector(sparse_bool_vector=random_sparse_bool_vector(dim))
    #     res = eknn.knn_query(test_index, pipeline_id, qopts, qvec)
    #     assert len(res["hits"]["hits"]) == 10
    #
    # def test_search_lsh_jaccard(self):
    #     test_index = pipeline_id = "python-test-lsh-jaccard"
    #     n, dim = 1200, 128
    #     propts = ProcessorOptions(field_raw=field_raw, dimension=dim,
    #                               jaccard_lsh=JaccardLshModelOptions(field_processed="vec_proc", num_bands=10, num_rows=2))
    #     eknn.create_pipeline(pipeline_id, propts)
    #     eknn.es.indices.delete(index=test_index, ignore=[400, 404])
    #     eknn.es.indices.refresh()
    #     eknn.es.indices.create(test_index)
    #     eknn.es.indices.refresh()
    #     eknn.prepare_mapping(test_index, propts)
    #     vecs = [random_sparse_bool_vector(dim) for _ in range(n)]
    #     (_, _) = eknn.index(test_index, pipeline_id, propts.field_raw, vecs)
    #     qopts = JaccardLshQueryOptions(num_candidates=10)
    #     qvec = ElastiKnnVector(sparse_bool_vector=random_sparse_bool_vector(dim))
    #     res = eknn.knn_query(test_index, pipeline_id, qopts, qvec, n_neighbors=10)
    #     assert len(res["hits"]["hits"]) == 10
