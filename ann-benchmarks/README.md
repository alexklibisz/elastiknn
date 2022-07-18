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

### Quick test

Make sure the environment is configured correctly.

```bash
# Start the cluster.
task cluster:start

# Test the released build.
task annb:test
```

### Benchmark a previous Elastiknn release

Run the ann-benchmarks against a previously-released version of Elastiknn, all in a container.
This benchmark constrains Elasticsearch to a single CPU core and will vary based on your hardware (primarily CPU).  

```bash
# Fashion MNIST dataset (smallest, fastest option)
task annb:benchmark-official-fashion-mnist
```

### Benchmark any version of Elastiknn

Run the an-benchmarks against an instance of Elastiknn available at `http://localhost:9200`.
This can be any version of Elastiknn, even a remote server, as long as it's available on localhost:9200. 
This is particularly useful for testing performance-sensitive changes before merging. 

```bash
# Run the local cluster.
task cluster:run

# Fashion MNIST dataset.
task annb:benchmark-local-fashion-mnist
```
