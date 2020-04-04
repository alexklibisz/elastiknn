import itertools
import os
import sys
import numpy as np
import matplotlib.pyplot as plt
from time import time

from elastiknn.models import ElastiknnModel

from utils import open_dataset, ANNB_ROOT, Dataset, pareto_max

INDEX = "ann-benchmarks-jaccard"


def evaluate(dataset: Dataset, eknn: ElastiknnModel):
    n_neighbors = len(dataset.queries[0].indices)
    eknn.fit(dataset.corpus, shards=1)
    t0 = time()
    neighbors_pred = eknn.kneighbors([q.vector for q in dataset.queries], allow_missing=True, n_neighbors=n_neighbors)
    queries_per_sec = len(dataset.queries) / (time() - t0)
    recalls = [
        len(set(q.indices).intersection(p)) / len(q.indices)
        for (q, p) in zip(dataset.queries, neighbors_pred)
    ]
    recall = sum(recalls) / len(recalls)
    return recall, queries_per_sec


def exact(dataset: Dataset):
    eknn = ElastiknnModel(algorithm='exact', metric='jaccard', n_jobs=1, index=f"{INDEX}-exact")
    return evaluate(dataset, eknn)


def indexed(dataset: Dataset):
    eknn = ElastiknnModel(algorithm='sparse_indexed', metric='jaccard', n_jobs=1, index=f"{INDEX}-indexed")
    return evaluate(dataset, eknn)


def lsh(dataset: Dataset, bands: int = 165, rows: int = 1, candidates: float = 1.5):
    n_neighbors = len(dataset.queries[0].indices)
    eknn = ElastiknnModel(algorithm='lsh', metric='jaccard', n_jobs=1, index=f"{INDEX}-lsh",
                          mapping_params={"bands": bands, "rows": rows},
                          query_params={"candidates": int(candidates * n_neighbors)})
    return evaluate(dataset, eknn)


def main():

    dsname = "kosarak-jaccard"

    # Load the dataset. This assumes you've run the download.sh script.
    dataset = open_dataset(os.path.join(ANNB_ROOT, f"{dsname}.hdf5"))
    print(f"Loaded {len(dataset.corpus)} vectors and {len(dataset.queries)} queries")

    # for _ in range(33):
    #     loss = exact(dataset)
    #     print(f"exact: {loss}")
    #
    # for _ in range(3):
    #     loss = indexed(dataset)
    #     print(f"jaccard indexed: {loss}")

    for _ in range(3):
        loss = lsh(dataset, 165, 1, 1.5)
        print(f"lsh: {loss}")

    bands = [('bands', b) for b in range(10, 601, 10)]
    rows = [('rows', r) for r in range(1, 2)]
    candidates = [('candidates', c) for c in np.linspace(0, 10, 21)]

    combinations = list(map(dict, itertools.product(bands, rows, candidates)))
    metrics = np.zeros((len(combinations), 2))

    for i, params in enumerate(combinations):
        print(f"Running {i + 1} of {len(combinations)}: {params}...")
        try:
            (x, y) = lsh(dataset, **params)
            print(f"Loss = {(x, y)}")
            metrics[i] = [x, y]
            pmax = pareto_max(metrics)

            plt.title(f"{dsname} results")
            plt.scatter(metrics[:, 0], metrics[:, 1], label='All')
            plt.scatter(metrics[pmax, 0], metrics[pmax, 1], label='Optimal')
            plt.legend()
            plt.savefig(f"out/{dsname}.png")
            plt.clf()

            with open(f"out/{dsname}.txt", "w") as fp:
                for j in pmax:
                    d, m = combinations[j], metrics[j]
                    fp.write(f"{d['num_bands']}, {d['num_rows']}, {m[0]}, {m[1]}\n")
        except Exception as e:
            print(e, file=sys.stderr)
            continue
        finally:
            print('-' * 100)


if __name__ == "__main__":
    main()