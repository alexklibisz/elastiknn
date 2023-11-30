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

    def test_sorting(self):
        eknn = ElastiknnClient()
        n, dim = 1200, 42
        index = "py-test-sorting"
        vec_field = "vec"
        id_field = "id"
        sort_by_distance_field = "vec_distance_from_origin"
        mapping = Mapping.DenseFloat(dims=dim)

        eknn.es.indices.delete(index=index, ignore_unavailable=True)
        eknn.es.indices.refresh()
        eknn.create_index(index, vec_field, mapping, id_field, sort_by_distance_field)
        eknn.es.indices.refresh()

        vecs = [Vec.DenseFloat.random(dim) for _ in range(n)]
        ids = [f"vec-{i}" for i in range(n)]
        (n_, errors) = eknn.index(index, vec_field, vecs, id_field, ids, refresh=True,
                                  sort_by_distance_field=sort_by_distance_field)
        assert n_ == n
        assert len(errors) == 0

        settings = eknn.es.indices.get_settings(index=index)
        sort_settings = settings.body[index]["settings"]["index"]["sort"]
        assert sort_settings["field"] == [sort_by_distance_field]
        assert sort_settings["order"] == ["asc"]

        results = eknn.es.search(index=index)
        for hit in results.body["hits"]["hits"]:
            src = hit["_source"]
            assert src[sort_by_distance_field] > 0, src
