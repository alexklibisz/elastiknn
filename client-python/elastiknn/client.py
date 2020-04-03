from typing import Iterable

from elasticsearch import Elasticsearch
from elasticsearch.helpers import bulk

from .api import *


class ElastiKnnClient(object):

    def __init__(self, hosts: List[str] = None):
        if hosts is None:
            hosts = ["http://localhost:9200"]
        self.hosts = hosts
        self.es = Elasticsearch(self.hosts)

    def put_mapping(self, index: str, field: str, mapping: Mapping.Base):
        body = {
            "properties": {
                field: mapping.to_dict()
            }
        }
        return self.es.transport.perform_request("PUT", f"/{index}/_mapping", body=body)

    def index(self, index: str, field: str, vecs: Iterable[Vec.Base], ids: List[str] = None, refresh: bool = False) -> (int, List):
        if ids is None or ids == []:
            ids = [None for _ in vecs]

        def gen():
            for vec, _id in zip(vecs, ids):
                d = { "_op_type": "index", "_index": index, field: vec.to_dict()}
                if _id:
                    d["_id"] = str(_id)
                elif "_id" in d:
                    del d["_id"]
                yield d

        res = bulk(self.es, gen())
        if refresh:
            self.es.indices.refresh(index=index)
        return res

    def nearest_neighbors(self, index: str, query: NearestNeighborsQuery.Base, k: int = 10, fetch_source: bool = False):
        body = {
            "query": {
                "elastiknn_nearest_neighbors": query.to_dict()
            }
        }
        return self.es.search(index, body=body, size=k, _source=fetch_source)
