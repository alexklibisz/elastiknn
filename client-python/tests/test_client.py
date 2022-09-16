from elastiknn.api import Mapping, Vec, NearestNeighborsQuery, Similarity
from elastiknn.client import ElastiknnClient


class TestClient:

    def test_exact_jaccard(self):
        eknn = ElastiknnClient()
        n, dim = 1200, 42
        index = "py-test-exact-jaccard"
        vec_field = "vec"
        id_field = "id"
        mapping = Mapping.SparseBool(dims=dim)

        eknn.es.indices.delete(index=index, ignore_unavailable=True)
        eknn.es.indices.refresh()
        eknn.es.indices.create(index=index)
        eknn.es.indices.refresh()
        m = eknn.put_mapping(index, vec_field, mapping, "id")

        vecs = [Vec.SparseBool.random(dim) for _ in range(n)]
        ids = [f"vec-{i}" for i in range(n)]
        (n_, errors) = eknn.index(index, vec_field, vecs, id_field, ids, refresh=True)
        assert n_ == n
        assert len(errors) == 0

        qvec = vecs[0]
        query = NearestNeighborsQuery.Exact(vec_field, qvec, Similarity.Jaccard)
        res = eknn.nearest_neighbors(index, query, id_field, 10, False)
        hits = res['hits']['hits']
        assert len(hits) == 10
        assert hits[0]["fields"]["id"][0] == ids[0]

        res = eknn.nearest_neighbors(index, query, id_field, 10, True)
        hits = res['hits']['hits']
        assert len(hits) == 10
        assert hits[0]["_id"] == ids[0]
