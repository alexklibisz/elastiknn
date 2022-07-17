# ANN-Benchmarks Integration

This directory contains an integration with [ann-benchmarks](https://github.com/erikbern/ann-benchmarks), which is the primary benchmark suite for Elastiknn and many other nearest neighbor algorithms.

## Requirements

- Task ([taskfile.dev](https://taskfile.dev/#/installation))
- Python3.6 (`python3.6` executable on the path)
- Docker

## Recipes

### Setup Environment

```bash
# Checkout the ann-benchmarks submodule.
$ task annb:submodule

# Build a local virtual environment
$ task annb:venv
```

### Quick benchmark test

This is useful for making sure the environment is configured correctly.

```bash
task annb:test
```

### Benchmark an Elastiknn release

This produces the results submitted to the [ann-benchmarks repo](https://github.com/erikbern/ann-benchmarks) and tracked on [ann-benchmarks.com](http://ann-benchmarks.com/).
Note that this benchmark constrains Elasticsearch to a single CPU core, runs everything locally, and will vary based on your hardware (primarily CPU).  

```bash
# Fashion MNIST dataset (smallest, fastest option)
task annb:benchmark-release-fashion-mnist

# SIFT dataset
task annb:benchmark-release-sift
```

### Benchmark the local Elastiknn branch

This benchmarks an instance of Elastiknn built from the local branch.
This is particularly useful for testing performance-sensitive changes before merging. 

```bash
# Run the local cluster.
task cluster:run

# Fashion MNIST dataset (smallest, fastest option)
task annb:benchmark-local-fashion-mnist

# SIFT dataset
task annb:benchmark-local-sift
```

### Benchmark Elastiknn on any Elasticsearch cluster

...

### Generate a blog post with the latest results

This is the primary way we report benchmark results.

