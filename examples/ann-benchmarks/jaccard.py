import os
from time import time

import chocolate as ch
import chocolate.mo as chmo
import matplotlib.pyplot as plt
from elastiknn.models import ElastiKnnModel

from utils import open_dataset, ANNB_ROOT, Dataset


def evaluate(dataset: Dataset, num_tables: int, num_bands: int, num_rows: int):
    eknn = ElastiKnnModel(n_neighbors=len(dataset.queries[0].indices), algorithm='lsh', metric='jaccard', n_jobs=1,
                          algorithm_params=dict(num_tables=num_tables, num_bands=num_bands, num_rows=num_rows),
                          index="ann-benchmarks-jaccard")
    print("Indexing...")
    eknn.fit(dataset.corpus, shards=os.cpu_count() - 1, recreate_index=True)
    print("Searching...")
    t0 = time()
    neighbors_pred = eknn.kneighbors([q.vector for q in dataset.queries], return_distance=False, allow_missing=True,
                                     use_cache=True)
    queries_per_sec = len(dataset.queries) / (time() - t0)
    recalls = [
        len(set(q.indices).intersection(p)) / len(q.indices)
        for (q, p) in zip(dataset.queries, neighbors_pred)
    ]
    recall = sum(recalls) / len(recalls)
    return -1 * recall,  -1 * queries_per_sec


def main():

    dsname = "kosarak-jaccard"

    # Load the dataset. This assumes you've run the download.sh script.
    dataset = open_dataset(os.path.join(ANNB_ROOT, f"{dsname}.hdf5"))
    print(f"Loaded {len(dataset.corpus)} vectors and {len(dataset.queries)} queries")

    # Setup database for optimizing. Delete the database manually if needed.
    conn = ch.SQLiteConnection(f"sqlite:///out/{dsname}.db")

    # Setup space for optimizing.
    space = dict(
        num_tables=ch.quantized_uniform(10, 81, 5),
        num_bands=ch.quantized_uniform(4, 29, 4),
        num_rows=ch.quantized_uniform(1, 3, 1)
    )

    # Sample/evaluate loop.
    sampler = ch.MOCMAES(conn, space, mu=15)

    while True:
        token, params = sampler.next()
        print(f"Running with params: {params}")
        loss = evaluate(dataset, **params)
        print(f"Resulted in loss = {loss}")
        sampler.update(token, loss)

        results = conn.results_as_dataframe()
        results.to_csv(f"out/{dsname}.csv")

        losses = results[["_loss_0", "_loss_1"]].values
        front = chmo.argsortNondominated(losses, len(losses), first_front_only=True)
        losses *= -1
        plt.scatter(losses[:, 0], losses[:, 1], label="All candidates")
        plt.scatter(losses[front, 0], losses[front, 1], label="Optimal")
        plt.xlabel("Recall")
        plt.ylabel("Queries/second")
        plt.legend()
        plt.title(dsname)
        plt.savefig(f"out/{dsname}.png")
        plt.clf()


main()
