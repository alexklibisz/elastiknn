import itertools
from typing import List

from bokeh.plotting import ColumnDataSource, figure, output_file, save
from bokeh.palettes import Dark2_5 as palette

import pandas as pd


def pareto_frontier(df: pd.DataFrame, colx: str, coly: str, round_to: int = 2) -> pd.DataFrame:
    """Return the subset of the given dataframe comprising the pareto frontier."""
    return df.groupby(lambda i: df.loc[i, colx].round(2))[colx, coly].max()


def main():
    df = pd.read_csv('/home/alex/tmp/results/aggregate/aggregate.csv')
    output_file("results.html")
    colors = itertools.cycle(palette)
    for (dataset, g1) in df.groupby('dataset'):
        p = figure(plot_width=800, plot_height=400, title=dataset)
        for ((algorithm, k), g2) in g1.groupby(['algorithm', 'k']):
            color = next(colors)
            pareto = pareto_frontier(g2, 'averageRecall', 'queriesPerSecondPerShard', 2)
            print(g2.shape)
            print(pareto.shape)
            p.line(
                pareto['averageRecall'],
                pareto['queriesPerSecondPerShard'],
                color=color
            )
            p.circle(
                pareto['averageRecall'],
                pareto['queriesPerSecondPerShard'],
                legend_label=f"{algorithm}, {k}",
                color=color
            )
        save(p)

if __name__ == '__main__':
    main()