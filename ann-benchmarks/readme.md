# ANN-Benchmarks Integration

This directory contains an integration with [ann-benchmarks](https://github.com/erikbern/ann-benchmarks), which is the primary benchmark suite for Elastiknn.

## Requirements

- Task ([taskfile.dev](https://taskfile.dev/#/installation))
- Python3.6 (`python3.6` executable on the path)
- Docker

## Recipes

### Setup Environment

```bash
# Build a local virtual environment
$ task annb:venv

# Install the python dependencies.
$ task annb:requirements
```

### Hello World

This is useful for making sure the environment is setup correctly to run the benchmarks.

```bash
task annb:test
```

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

### Run benchmarks with VisualVM for profiling

### Run benchmarks with Intellij debugging
