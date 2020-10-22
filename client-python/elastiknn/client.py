"""Client for using Elastiknn."""

from typing import Iterable, Tuple, Dict, Optional

from elasticsearch import Elasticsearch
from elasticsearch.helpers import bulk

from .api import *


class ElastiknnClient(object):

    def __init__(self, es: Elasticsearch = None):
        """Wrapper on the official `elasticsearch.Elasticsearch` client for making Elastiknn requests.

        The client is fairly primitive in that it assumes the vector is basically the only thing stored in each doc.
        For more complex use-cases you should use this client as an example for constructing the mapping, indexing,
        and search requests in your own code.

        Parameters
        ----------
        es : `elasticsearch.Elasticsearch` client.
            This client is used internally to make all requests.
            Defaults to a client pointing at http://localhost:9200.
        """
        if es is None:
            self.es = Elasticsearch(["http://localhost:9200"], timeout=99)
        else:
            self.es = es

    def put_mapping(self, index: str, vec_field: str, mapping: Mapping.Base, stored_id_field: str):
        """
        Update the mapping at the given index and field to store an Elastiknn vector.

        Parameters
        ----------
        index : string
            Index containing the given field. Should already exist.
        vec_field : string
            Field containing the vector in each document. Uses the given mapping.
        mapping : instance of `Mapping.Base`
            Mapping object defining the vector's storage properties.
        stored_id_field : string
            Field containing the document ID. Uses `store: true` setting as an optimization for faster id-only queries.

        Returns
        -------
        Dict
            Json response as a dict. Successful request returns `{"acknowledged": true}`.
        """
        body = {
            "properties": {
                vec_field: mapping.to_dict(),
                stored_id_field: {
                    "type": "keyword",
                    "store": True
                }
            }
        }
        return self.es.transport.perform_request("PUT", f"/{index}/_mapping", body=body)

    def index(self, index: str, vec_field: str, vecs: Iterable[Vec.Base], stored_id_field: str, ids: Iterable[str], refresh: bool = False) -> Tuple[int, List[Dict]]:
        """Index (i.e. store) the given vectors at the given index and field with the optional ids.

        Parameters
        ----------
        index : string
            Index where the vectors are stored.
        vec_field : string
            Field containing the vector in each document.
        vecs : List of `Vec.Base`
            Vectors that should be indexed.
        ids : Iterable of strings
            Ids associated with the given vectors. Should have same length as vecs.
        stored_id_field:
            Field containing the document ID. Uses `store: true` setting as an optimization for faster id-only queries.
        refresh : bool
            Whether to refresh before returning. Set to true if you want to immediately run queries after indexing.

        Returns
        -------
        Int
            Number of vectors successfully indexed.
        List of Dicts
            Error responses for the vectors that were not indexed.
        """

        def gen():
            for vec, _id in zip(vecs, ids):
                yield { "_op_type": "index", "_index": index, vec_field: vec.to_dict(), stored_id_field: str(_id), "_id": str(_id) }

        res = bulk(self.es, gen(), chunk_size=200, max_retries=9)
        if refresh:
            self.es.indices.refresh(index=index)
        return res

    def nearest_neighbors(self, index: str, query: NearestNeighborsQuery.Base, stored_id_field: str, k: int = 10,
                          fetch_source: bool = False) -> Dict:
        """Build and execute a nearest neighbors query against the given index.

        Parameters
        ----------
        index : string
            Index to run the search against.
        query : NearestNeighborsQuery.Base
            Query object defining the query properties.
        stored_id_field:
            Field containing the document ID. Uses `store: true` setting as an optimization for faster id-only queries.
        k: int
            Number of hits to return.
        fetch_source : bool
            Whether to return the `_source` of the document. If you only need the ID, it's generally much faster to
            set this to False and instead of accessing the ID in hit['_id'], it will be in hit['fields'][stored_id_field][0].

        Returns
        -------
        Dict
            Standard Elasticsearch search response parsed as a dict.
        """
        body = {
            "query": {
                "elastiknn_nearest_neighbors": query.to_dict()
            }
        }
        if fetch_source:
            return self.es.search(index, body=body, size=k)
        else:
            return self.es.search(index, body=body, size=k, _source=fetch_source, docvalue_fields=stored_id_field,
                                  stored_fields="_none_",
                                  filter_path=[f'hits.hits.fields.{stored_id_field}', 'hits.hits._score'])
