# ANN-Benchmarks Integration

This directory contains an integration with [ann-benchmarks](https://github.com/erikbern/ann-benchmarks), which is the primary benchmark suite for Elastiknn.

## Recipes

### Hello World

### Run benchmarks in containers

This is useful for reproducing the results submitted to the [ann-benchmarks repo](https://github.com/erikbern/ann-benchmarks) and tracked on [ann-benchmarks.com](http://ann-benchmarks.com/).

Note that this benchmark constrains Elasticsearch to a single CPU core and runs everything locally. 
For those interested in a benchmark involving parallelism and a real network, consider the next section.  

### Run benchmarks against any Elasticsearch cluster

This is useful for benchmarking against a real Elasticsearch cluster, which is more realistic than the containerized benchmark.

The benchmark program still runs locally.
The only requirements is an Elasticsearch cluster with security disabled, accessible via localhost:9200.  

### Generate a blog post with the latest results

This is the primary way we report benchmark results. 