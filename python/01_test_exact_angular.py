from time import sleep

from elasticsearch import Elasticsearch
from requests import *
from pprint import pprint

es = Elasticsearch()
es.ping()

url = "http://localhost:9200"
index = "elastiknn-index-01"
pipeline = "elastiknn-pipeline-01"

res = put(f"{url}/_ingest/pipeline/{pipeline}", json={
    "description": "elastiknn pipeline 1",
    "processors": [
        {
            "elastiknn": {
                "fieldRaw": "vec_raw",
                "fieldProcessed": "vec_proc",
                "dimension": 2,
                "exact": {}
            }
        }
    ]
})
print(res.status_code)
pprint(res.json())
assert res.status_code == 200

delete(f"{url}/{index}")

res = put(f"{url}/{index}", json={})
print(res.status_code)
pprint(res.json())
assert res.status_code == 200

res = post(f"{url}/{index}/_doc?pipeline={pipeline}", json={
    "vec_raw": [0.00, 0.11]
})
print(res.status_code)
pprint(res.json())
assert res.status_code == 201

# VERY IMPORTANT TO REFRESH HERE!
res = post(f"{url}/_refresh")

res = get(f"{url}/{index}/_search", json={
    "query": {
        "match_all": {}
    }
})
print(res.status_code)
pprint(res.json())

res = get(f"{url}/{index}/_search", json={
    "query": {
        "elastiknn_knn": {}
    }
})
print(res.status_code)
pprint(res.json())

# res = get(f"{url}/_search", json={
#     "query": {
#         "elastiknn_knn": {
#             "pipelineId": "elastiknn-pipeline-0",
#             "k": 2,
#             "exact": {
#                 "distance": "DISTANCE_ANGULAR"
#             },
#             "given": {
#                 "vector": [0.11, 0.22]
#             }
#         }
#     }
# })
# print(res.status_code)
# pprint(res.json())

print("done")