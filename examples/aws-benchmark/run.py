import json
import sys
from time import time
from typing import Tuple

from elasticsearch import Elasticsearch
from elasticsearch.helpers import bulk
from sklearn.neighbors import NearestNeighbors
import numpy as np
from tqdm import tqdm


def aws(train, test) -> Tuple[np.ndarray, float]:
    """Take numpy arrays of test and train vectors, return array of nearest neighbor ids, search time."""
    es = Elasticsearch()
    ix = "aws-cifar"
    es.indices.delete(ix, ignore=[400, 404])
    body = {
        "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "index": {
                "knn": True,
                # "knn.algo_param.m": 100,
                # "knn.algo_param.ef_search": 512,
                # "knn.algo_param.ef_construction": 512
            }
        },
        "mappings": {
            "properties": {
                "vec": {
                    "type": "knn_vector",
                    "dimension": train.shape[-1]
                }
            }
        }
    }
    es.indices.create(ix, body=json.dumps(body))
    es.indices.refresh(ix)

    def gen():
        for i, vec in enumerate(train):
            yield { "_op_type": "index", "_index": ix, "vec": list(map(float, vec)), "_id": int(i + 1) }

    (n, errs) = bulk(es, gen())
    assert len(errs) == 0, errs
    es.indices.refresh(index=ix)

    queries = [
        {
            "query": {
                "knn": {
                    "vec": {
                        "vector": list(map(float, vec)),
                        "k": 10
                    }
                }
            }
        }
        for vec in test
    ]

    t0 = time()
    results = [es.search(ix, body=q, _source=False) for q in tqdm(queries)]
    t1 = time()

    neighbors = np.array([
        [int(h["_id"]) - 1 for h in r["hits"]["hits"]]
        for r in results
    ])

    return neighbors, t1 - t0


def elastiknn(train, test) -> Tuple[np.ndarray, float]:
    """Take numpy arrays of test and train vectors, return array of nearest neighbor ids, search time."""
    from elastiknn.models import ElastiknnModel
    eknn = ElastiknnModel(algorithm='exact', metric='l2', n_jobs=1, index="elastiknn-cifar")
    eknn.fit(train, shards=1)
    while True:
        t0 = time()
        pred = eknn.kneighbors(test, n_neighbors=10, progbar=True)
        t1 = time()
        print(t1 - t0)
    return pred, t1 - t0


def flatten(arr):
    return arr.reshape(len(arr), np.prod(arr.shape[1:]))


if __name__ == "__main__":
    assert len(sys.argv) == 2, "Usage: <script.py> (elastiknn | aws)"
    which = sys.argv[1]
    assert which in {"elastiknn", "aws"}, f"Expected 'elastiknn' or 'aws' but got '{which}'"

    # Load datasets. Keras is overkill for this in general but it has a nice datasets module.
    from keras.datasets import cifar100

    (train, _), (test, _) = cifar100.load_data('fine')
    train = flatten(train) / 256.0
    test = flatten(test[:1000]) / 256.0

    pred, tsearch = aws(train, test) if which == 'aws' else elastiknn(train, test)

    knn = NearestNeighbors(n_neighbors=10, algorithm='brute', metric='l2', n_jobs=-1)
    knn.fit(train)
    true = knn.kneighbors(test, return_distance=False)

    intersections = [len(set(t).intersection(p)) for t, p in zip(true, pred)]
    recall = sum(intersections) / np.prod(true.shape)

    print("recall = %.3lf" % recall)
    print("queries = %d" % len(pred))
    print("tsearch = %d" % tsearch)
    print("queries / sec = %.3lf" % (len(pred) / tsearch))
