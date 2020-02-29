import itertools
import os
import sys
from time import time

import matplotlib.pyplot as plt
import numpy as np
from elastiknn.models import ElastiKnnModel

from utils import open_dataset, ANNB_ROOT, Dataset, pareto_max


def evaluate(dataset: Dataset, num_bands: int, num_rows: int):
    index = "ann-benchmarks-jaccard"
    pipe = f"ingest-{index}-{num_bands}-{num_rows}"
    eknn = ElastiKnnModel(n_neighbors=len(dataset.queries[0].indices), algorithm='lsh', metric='jaccard', n_jobs=1,
                          algorithm_params=dict(num_bands=num_bands, num_rows=num_rows),
                          index="ann-benchmarks-jaccard", pipeline_id=pipe)
    # print("Checking subset...")
    # eknn.fit(dataset.corpus[:100], shards=os.cpu_count() - 1, recreate_index=True)
    # eknn.kneighbors([q.vector for q in dataset.queries[:5]], return_distance=False, allow_missing=True)
    print("Indexing...")
    eknn.fit(dataset.corpus, shards=os.cpu_count() - 1, recreate_index=True)
    eknn.kneighbors([q.vector for q in dataset.queries[:1]], return_distance=False, allow_missing=True)
    # print("Searching...")
    # t0 = time()
    # neighbors_pred = eknn.kneighbors([q.vector for q in dataset.queries], return_distance=False, allow_missing=True, use_cache=True)
    # queries_per_sec = len(dataset.queries) / (time() - t0)
    # recalls = [
    #     len(set(q.indices).intersection(p)) / len(q.indices)
    #     for (q, p) in zip(dataset.queries, neighbors_pred)
    # ]
    # recall = sum(recalls) / len(recalls)
    # return recall,  queries_per_sec
    return 0, 0


if __name__ == "__main__":

    dsname = "kosarak-jaccard"

    # Load the dataset. This assumes you've run the download.sh script.
    dataset = open_dataset(os.path.join(ANNB_ROOT, f"{dsname}.hdf5"))
    print(f"Loaded {len(dataset.corpus)} vectors and {len(dataset.queries)} queries")

    # Useful for sampling/profiling.
    while True:
        loss = evaluate(dataset, 165, 1)
        print(loss)

    num_bands = [('num_bands', b) for b in range(10, 601, 10)]
    num_rows = [('num_rows', r) for r in range(1, 2)]

    combinations = list(map(dict, itertools.product(num_bands, num_rows)))
    metrics = np.zeros((len(combinations), 2))

    for i, params in enumerate(combinations):
        print(f"Running {i + 1} of {len(combinations)}: {params}...")
        try:
            (x, y) = evaluate(dataset, **params)
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
