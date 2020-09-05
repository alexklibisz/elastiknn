import itertools
import json
from typing import List

from bokeh.layouts import column
from bokeh.models import Legend
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


def cleanup_mapping(s: str) -> str:
    d = json.loads(s)['elastiknn']
    return json.dumps(d)


def main():
    df = pd.read_csv('/home/alex/Desktop/results-1597606608/aggregate.csv')
    output_file("results.html", title="Elastiknn Benchmark Results")
    colors = itertools.cycle(palette)
    tooltips = [("mapping", "@mapping"), ("query", "@query")]
    xcol = "recall"
    ycol = "queriesPerSecond"
    figures = []
    for (dataset, g1) in df.groupby('dataset'):
        print(dataset)
        p = figure(plot_width=1600, plot_height=800, title=dataset, tooltips=tooltips,
                   x_axis_label="Average Recall", y_axis_label="Queries / Second / Shard",
                   toolbar_location="above")
        legend_items = []
        for ((algorithm, k), g2) in g1.groupby(['algorithm', 'k']):
            print(algorithm, k)
            legend_label = f"{algorithm}, {k}"
            color = next(colors)
            pareto = pareto_frontier(g2, xcol, ycol, 2)
            source_full = ColumnDataSource(data=dict(
                x=g2[xcol],
                y=g2[ycol].values,
                mapping=list(map(cleanup_mapping, g2['mapping'])),
                query=list(map(cleanup_query, g2['query']))
            ))
            source_pareto = ColumnDataSource(data=dict(
                x=pareto[xcol],
                y=pareto[ycol].values,
                mapping=list(map(cleanup_mapping, pareto['mapping'])),
                query=list(map(cleanup_query, pareto['query']))
            ))
            legend_items += [(
                legend_label,
                [
                    p.circle('x', 'y', color=color, fill_alpha=0.2, line_alpha=0.0, source=source_full),
                    p.line(pareto[xcol], pareto[ycol], color=color),
                    p.circle('x', 'y', color=color, source=source_pareto)
                ]
             )]

        legend = Legend(items=legend_items, location="center")
        legend.click_policy = "hide"
        p.add_layout(legend, "right")
        figures.append(p)
    save(column(*figures))


if __name__ == '__main__':
    main()