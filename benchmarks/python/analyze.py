import itertools
import json
from typing import List

from bokeh.plotting import ColumnDataSource, figure, output_file, save
from bokeh.palettes import Dark2_5 as palette

import pandas as pd
import pandasql as ps


def pareto_frontier(df: pd.DataFrame, colx: str, coly: str, round_to: int = 2) -> pd.DataFrame:
    """Return the subset of the given dataframe comprising the pareto frontier."""
    # For some reason this type of aggregation is abysmally difficult with pure pandas.
    cols = ",".join(filter(lambda c: c not in {colx, coly}, df.columns))
    q = f"select {cols}, {colx}, max({coly}) as {coly} from df group by(round({colx}, {round_to}))"
    x = ps.sqldf(q, dict(df=df))
    return x

def cleanup_query(s: str) -> str:
    d = { k:v for (k,v) in json.loads(s).items() if k not in {'field', 'vec'} }
    return json.dumps(d)


def main():
    df = pd.read_csv('/home/alex/tmp/results/aggregate/aggregate.csv')
    output_file("results.html")
    colors = itertools.cycle(palette)
    tooltips = [("mapping", "@mapping"), ("query", "@query")]
    xcol = "averageRecall"
    ycol = "queriesPerSecondPerShard"
    for (dataset, g1) in df.groupby('dataset'):
        print(dataset)
        p = figure(plot_width=800, plot_height=400, title=dataset, tooltips=tooltips)
        for ((algorithm, k), g2) in g1.groupby(['algorithm', 'k']):
            print(algorithm, k)
            color = next(colors)
            pareto = pareto_frontier(g2, xcol, ycol, 2)
            source = ColumnDataSource(data=dict(
                x=pareto[xcol],
                y=pareto[ycol].values,
                mapping=pareto['mappingJson'],
                query=list(map(cleanup_query, pareto['queryJson']))
            ))
            p.line(pareto['averageRecall'], pareto['queriesPerSecondPerShard'], color=color)
            p.circle('x', 'y', legend_label=f"{algorithm}, {k}", color=color, source=source)
        save(p)

if __name__ == '__main__':
    main()