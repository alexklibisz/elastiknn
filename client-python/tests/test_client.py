from elastiknn.api import Mapping, Vec, NearestNeighborsQuery, Similarity
from elastiknn.client import ElastiKnnClient


class TestClient:

    def test_exact_jaccard(self):
        eknn = ElastiKnnClient()
        n, dim = 1200, 42
        index = "py-test-exact-jaccard"
        field = "vec"
        mapping = Mapping.SparseBool(dims=dim)

        eknn.es.indices.delete(index, ignore=[400, 404])
        eknn.es.indices.refresh()
        eknn.es.indices.create(index)
        eknn.es.indices.refresh()
        m = eknn.put_mapping(index, field, mapping)

        vecs = [Vec.SparseBool.random(dim) for _ in range(n)]
        ids = [f"vec-{i}" for i in range(n)]
        (n_, errors) = eknn.index(index, field, vecs, ids, refresh=True)
        assert n_ == n
        assert len(errors) == 0

        qvec = vecs[0]
        query = NearestNeighborsQuery.Exact(field, qvec, Similarity.Jaccard)
        res = eknn.nearest_neighbors(index, query, 10, False)
        hits = res['hits']['hits']
        assert len(hits) == 10
        assert hits[0]["_id"] == ids[0]
