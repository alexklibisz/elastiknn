"""
Generates a markdown report from the aggregate CSV file produced by the elastiknn-benchmarks project.
Designed to be copied into the markdown documentation.
Render to HTML using pandoc:
  python report.py /path/to/aggregate.csv | pandoc --from=gfm -t html -o out.html
  open out.html
"""
import itertools
import json

import pandas as pd
from sys import argv
from io import BytesIO
import matplotlib.pyplot as plt
from pareto import eps_sort


def pareto_frontier(df: pd.DataFrame, colx: str, coly: str) -> pd.DataFrame:
    cxi = list(df.columns).index(colx)
    cyi = list(df.columns).index(coly)
    rows = eps_sort([list(df.itertuples(False))], objectives=[cxi, cyi], maximize=[cxi, cyi])
    return pd.DataFrame(rows, columns=df.columns).sort_values([colx])


def cleanup_query(s: str) -> str:
    d = {k: v for (k, v) in json.loads(s).items() if k not in {'field', 'vec', 'limit'}}
    return json.dumps(d)


def cleanup_mapping(s: str) -> str:
    eknn = json.loads(s)['elastiknn']
    return json.dumps(eknn)


def cleanup_dataset(s: str) -> str:
    return ''.join(map(lambda c: c if c.islower() else f" {c}", s)).strip()


def main():
    assert len(argv) == 2, "Usage: <script> path/to/aggregate.csv"
    aggdf = pd.read_csv(argv[1]).dropna()

    aggdf["query"] = aggdf["query"].apply(cleanup_query)
    aggdf["mapping"] = aggdf["mapping"].apply(cleanup_mapping)
    aggdf["recall"] = aggdf["recall"].round(2)
    aggdf["queriesPerSecond"] = aggdf["queriesPerSecond"].round(0)
    aggdf["dataset"] = aggdf["dataset"].apply(cleanup_dataset)

    for (dataset, dsetdf) in aggdf.groupby("dataset"):

        print(f"### {dataset}")

        for (shards, sharddf) in dsetdf.groupby("shards"):
            shardstr = "Single Shard" if shards == 1 else f"{shards} Shards"
            print(f"#### {shardstr}")

            colors = itertools.cycle(list('bgrcmykw'))

            plt.title(f"{dataset} - {shardstr}")
            plt.xlabel("Recall")
            plt.ylabel("Queries/Second")
            plt.xlim(0, 1.05)
            plt.grid(True, linestyle='--', linewidth=0.5)

            paretos = []

            for ((algo, k), algodf) in dsetdf.groupby(['algorithm', 'k']):
                paretodf = pareto_frontier(algodf, "recall", "queriesPerSecond")
                label = f"{algo} {k}"
                color = next(colors)
                marker = 'x' if algo == "Exact" else 'o'
                size = 20 if algo == "Exact" else 10
                plt.plot(paretodf["recall"], paretodf["queriesPerSecond"], color=color)
                plt.scatter(paretodf["recall"], paretodf["queriesPerSecond"], label=label, color=color, s=size, marker=marker)
                paretos.append((label, paretodf))

            plt.legend(loc='lower left')

            # Save the plot to an SVG in an in-memory buffer. Drop the first four lines of xml/svg metadata tags.
            buf = BytesIO()
            plt.savefig(buf, format="svg")
            plt.clf()
            buf.seek(0)
            print(' '.join(map(str.strip, buf.read().decode().split('\n')[4:])))

            for (label, df) in paretos:
                print(f"**{label}**\n")
                rename = {"recall": "Recall", "queriesPerSecond": "Q/S", "mapping": "Mapping", "query": "Query"}
                df2 = df\
                    .rename(index=str, columns=rename)\
                    .sort_values(["Recall", "Q/S"], ascending=False)[list(rename.values())]\
                    .to_markdown(index=False)
                print(f"{df2}\n")

        print("---")


if __name__ == '__main__':
    main()
