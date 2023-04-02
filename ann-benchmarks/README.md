# ANN-Benchmarks Integration

This directory contains an integration with [ann-benchmarks](https://github.com/erikbern/ann-benchmarks), the primary tool for benchmarking Elastiknn.

## Requirements

- Task ([taskfile.dev](https://taskfile.dev/#/installation))
- Python3.6 (`python3.6` executable on the path)
- Docker

## Tips

* To benchmark with multiple shards in the index, increase the `thread_pool.search.queue_size` setting in Dockerfile.elastiknn to at least the number of shards. Otherwise, the Elasticsearch server will return fewer than the desired number of neighbors.

## Recipes

### Setup Environment

```bash
# Checkout the ann-benchmarks repo as a submodule.
$ task annbCreateSubmodule

# Install Python and Docker dependencies.
$ task annbInstallRequirements
```

### Quick test

Make sure the environment is configured correctly.

```bash
# Start the local cluster.
task dockerRunTestingCluster

# Test the released build.
task annbTest
```

### Benchmark a previous Elastiknn release

Run ann-benchmarks on a previously-released version of Elastiknn, all in a container.
This benchmark constrains Elasticsearch to a single CPU core and will vary based on your hardware (primarily CPU).  

```bash
# Fashion MNIST dataset.
task annbRunOfficialFashionMnist
```

### Benchmark Elastiknn on the local branch

Run the an-benchmarks on an instance of Elastiknn available at `http://localhost:9200`.
This can be any version of Elastiknn, even a remote server, as long as it's available on `http://localhost:9200`. 
This is particularly useful for testing performance-sensitive changes before merging. 

```bash
# Start the local cluster.
task dockerRunTestingCluster

# Fashion MNIST dataset.
task annbRunOfficialFashionMnist
```
