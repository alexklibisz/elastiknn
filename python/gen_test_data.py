import json
import sys
from dataclasses import dataclass
from typing import List, Union

import numpy as np
from dataclasses_json import dataclass_json
from sklearn.neighbors import NearestNeighbors


@dataclass_json
@dataclass
class Query:
    vector: List[Union[float, bool]]
    distances: List[float]
    indices: List[int]


@dataclass_json
@dataclass
class TestData:
    corpus: List[Union[float, bool]]
    queries: List[Query]


def gen_test_data(dim: int, corpus_size: int, num_queries: int, metric: str, output_path: str) -> TestData:
    np.random.seed(dim)

    bitwise = metric is 'hamming' or metric is 'jaccard'
    metric = 'cosine' if metric is 'angular' else metric
    if bitwise:
        corpus = np.random.randint(2, size=(corpus_size, dim), dtype=bool)
        queries = np.random.randint(2, size=(num_queries, dim), dtype=bool)
    else:
        corpus = np.random.rand(corpus_size, dim)
        queries = np.random.rand(num_queries, dim)

    f = bool if bitwise else float
    corpus = [list(map(f, _)) for _ in corpus]
    queries = [list(map(f, _)) for _ in queries]

    knn = NearestNeighbors(n_neighbors=corpus_size, algorithm='brute', metric=metric)
    (dists, inds) = knn.fit(corpus).kneighbors(queries)

    queries = [
        Query(vector=vector, distances=list(map(float, dists_)), indices=list(map(int, inds_)))
        for (vector, dists_, inds_) in zip(queries, dists, inds)
    ]
    test_data = TestData(corpus=corpus, queries=queries)
    with open(output_path, "w") as fp:
        json.dump(test_data.to_dict(), fp)
    print(f"Saved {dim}-dimensional {metric} to {output_path}")


def main(argv: List[str]):
    assert len(argv) == 2
    output_dir = argv[1]
    metrics = ['l1', 'l2', 'angular', 'hamming', 'jaccard']
    dims = [10, 128, 512]

    for dim in dims:
        for metric in metrics:
            gen_test_data(dim, 100, 10, metric, f'{output_dir}/distance_{metric}-{dim}.json')


if __name__ == "__main__":
    main(sys.argv)