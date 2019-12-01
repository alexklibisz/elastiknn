import json
from dataclasses import dataclass, field
from typing import List, Callable

import numpy as np
from dataclasses_json import dataclass_json, config
from google.protobuf.json_format import MessageToDict
from sklearn.neighbors import NearestNeighbors

from elastiknn.elastiknn_pb2 import *


@dataclass_json
@dataclass
class Query:
    vector: ElastiKnnVector = field(metadata=config(encoder=MessageToDict))
    similarities: List[float]
    indices: List[int]


@dataclass_json
@dataclass
class TestData:
    corpus: List[ElastiKnnVector] = field(metadata=config(encoder=lambda vecs: list(map(MessageToDict, vecs))))
    queries: List[Query]


def dist2sim(metric: str) -> Callable[[float], float]:
    if metric is 'cosine':
        return lambda d: 2.0 - d
    elif metric in {'l1', 'l2', 'hamming'}:
        return lambda d: 1.0 / (d + 1e-6)
    elif metric is 'jaccard':
        return lambda d: 1 - d
    else:
        return lambda d: d


def gen_test_data(dim: int, corpus_size: int, num_queries: int, metric: str, output_path: str) -> TestData:
    np.random.seed(dim)

    boolean = metric in {"hamming", "jaccard"}
    metric = 'cosine' if metric is 'angular' else metric

    d2s = dist2sim(metric)

    if boolean:
        np_corpus_vecs = np.random.randint(2, size=(corpus_size, dim), dtype=bool)
        np_query_vecs = np.random.randint(2, size=(num_queries, dim), dtype=bool)
    else:
        np_corpus_vecs = np.random.rand(corpus_size, dim)
        np_query_vecs = np.random.rand(num_queries, dim)

    knn = NearestNeighbors(n_neighbors=10, algorithm='brute', metric=metric)
    (dists, inds) = knn.fit(np_corpus_vecs).kneighbors(np_query_vecs)

    if boolean:
        pb_corpus_vecs = [ElastiKnnVector(bool_vector=BoolVector(values=list(map(bool, _)))) for _ in np_corpus_vecs]
        pb_query_vecs = [ElastiKnnVector(bool_vector=BoolVector(values=list(map(bool, _)))) for _ in np_query_vecs]
    else:
        pb_corpus_vecs = [ElastiKnnVector(double_vector=DoubleVector(values=list(map(float, _)))) for _ in
                          np_corpus_vecs]
        pb_query_vecs = [ElastiKnnVector(double_vector=DoubleVector(values=list(map(float, _)))) for _ in np_query_vecs]

    queries = [
        Query(vector=vec, similarities=list(map(lambda d: d2s(float(d)), dists_)), indices=list(map(int, inds_)))
        for (vec, dists_, inds_) in zip(pb_query_vecs, dists, inds)
    ]
    test_data = TestData(corpus=pb_corpus_vecs, queries=queries)
    with open(output_path, "w") as fp:
        json.dump(test_data.to_dict(), fp)
    print(f"Saved {dim}-dimensional {metric} to {output_path}")


def main(argv: List[str]):
    assert len(argv) == 2, "please give the output directory"
    output_dir = argv[1]
    metrics = ['l1', 'l2', 'angular', 'hamming', 'jaccard']
    dims = [10, 128, 512]

    for dim in dims:
        for metric in metrics:
            gen_test_data(dim, 100, 10, metric, f'{output_dir}/similarity_{metric}-{dim}.json')


if __name__ == "__main__":
    main(sys.argv)
