## Benchmarks

This project includes Terraform "code" for setting up a K8s cluster with Argo workflows.
The benchmarks are very much a work in progress.
The end goal is to have an end-to-end pipeline that meausres the performance of many mapping
and query configurations for a dozen or so datasets and produces nice Bokeh graphs showing
the Pareto Frontier for each (dataset, algorithm, number of neighbors) tuple.