import itertools
import json

import pandas as pd
from bokeh.layouts import column
from bokeh.models import Legend
from bokeh.palettes import Dark2_5 as palette
from bokeh.plotting import ColumnDataSource, figure, output_file, save
from pareto import eps_sort


def pareto_frontier(df: pd.DataFrame, colx: str, coly: str) -> pd.DataFrame:
    cxi = list(df.columns).index(colx)
    cyi = list(df.columns).index(coly)
    rows = eps_sort([list(df.itertuples(False))], objectives=[cxi, cyi], maximize=[cxi, cyi])
    return pd.DataFrame(rows, columns=df.columns).sort_values([colx])


def cleanup_query(s: str) -> str:
    d = { k:v for (k,v) in json.loads(s).items() if k not in {'field', 'vec'} }
    return json.dumps(d)


def cleanup_mapping(s: str) -> str:
    d = json.loads(s)['elastiknn']
    return json.dumps(d)


def main():
    df = pd.read_csv('~/Desktop/aggregate.csv').dropna()
    output_file("results.html", title="Elastiknn Benchmark Results")
    colors = itertools.cycle(palette)
    tooltips = [("mapping", "@mapping"), ("query", "@query"), ("recall", "@recall"), ("queries", "@queries")]
    xcol = "recall"
    ycol = "queriesPerSecond"
    figures = []
    for (dataset, g1) in df.groupby('dataset'):
        print(dataset)
        p = figure(plot_width=1600, plot_height=800, title=dataset, tooltips=tooltips,
                   x_axis_label="Average Recall", y_axis_label="Queries/Second",
                   toolbar_location="above")
        legend_items = []
        for ((algorithm, k), g2) in g1.groupby(['algorithm', 'k']):
            print(algorithm, k)
            legend_label = f"{algorithm}, {k}"
            color = next(colors)
            pareto = pareto_frontier(g2, xcol, ycol)
            source_full = ColumnDataSource(data=dict(
                x=g2[xcol],
                y=g2[ycol].values,
                mapping=list(map(cleanup_mapping, g2['mapping'])),
                query=list(map(cleanup_query, g2['query'])),
                recall=list(g2['recall']),
                queries=list(g2['queriesPerSecond'])
            ))
            source_pareto = ColumnDataSource(data=dict(
                x=pareto[xcol],
                y=pareto[ycol].values,
                mapping=list(map(cleanup_mapping, pareto['mapping'])),
                query=list(map(cleanup_query, pareto['query'])),
                recall=list(pareto['recall']),
                queries=list(pareto['queriesPerSecond'])
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