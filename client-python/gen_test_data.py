import json
import sys
from typing import List, Callable

import numpy as np
from dataclasses import dataclass, field
from dataclasses_json import dataclass_json, config
from sklearn.neighbors import NearestNeighbors

from elastiknn.api import Vec
from elastiknn.utils import ndarray_to_sparse_bool_vectors, ndarray_to_dense_float_vectors


@dataclass_json
@dataclass
class Query:
    vector: Vec
    similarities: List[float] = field(metadata=config(encoder=lambda xx: list(map(float, xx))))
    indices: List[int] = field(metadata=config(encoder=lambda xx: list(map(float, xx))))


@dataclass_json
@dataclass
class TestData:
    corpus: List[Vec] # = field(metadata=config(encoder=lambda vecs: list(map(MessageToDict, vecs))))
    queries: List[Query]


def dist2sim(metric: str) -> Callable[[float], float]:
    if metric is 'cosine':
        return lambda d: 2.0 - d
    elif metric in {'l1', 'l2'}:
        return lambda d: 1.0 / (d + 1e-6)
    elif metric in {'jaccard', 'hamming'}:
        return lambda d: 1 - d
    else:
        return lambda d: d


def gen_test_data(dim: int, corpus_size: int, num_queries: int, num_neighbors: int, metric: str, output_path: str):
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

    knn = NearestNeighbors(n_neighbors=num_neighbors, algorithm='brute', metric=metric)
    (dists, inds) = knn.fit(np_corpus_vecs).kneighbors(np_query_vecs)

    if boolean:
        corpus_vecs = list(ndarray_to_sparse_bool_vectors(np_corpus_vecs))
        query_vecs = list(ndarray_to_sparse_bool_vectors(np_query_vecs))
    else:
        corpus_vecs = list(ndarray_to_dense_float_vectors(np_corpus_vecs))
        query_vecs = list(ndarray_to_dense_float_vectors(np_query_vecs))

    queries = [
        Query(vector=vec, similarities=list(map(lambda d: d2s(float(d)), dists_)), indices=list(map(int, inds_)))
        for (vec, dists_, inds_) in zip(query_vecs, dists, inds)
    ]
    test_data = TestData(corpus=corpus_vecs, queries=queries)
    with open(output_path, "w") as fp:
        json.dump(test_data.to_dict(), fp)
    print(f"Saved {dim}-dimensional {metric} to {output_path}")


def main(argv: List[str]):
    output_dir = argv[1] if len(argv) == 2 else "../testing/src/main/resources/com/klibisz/elastiknn/testing"
    metrics = ['jaccard', 'hamming', 'l1', 'l2', 'angular']
    dims = [10, 128, 512]

    for dim in dims:
        for metric in metrics:
            gen_test_data(dim, 100, 10, 30, metric, f'{output_dir}/similarity_{metric}-{dim}.json')


if __name__ == "__main__":
    main(sys.argv)
