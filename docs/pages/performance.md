---
layout: single
title: Performance
description: "Elastiknn Performance"
permalink: /performance/
classes: wide
---

## Method

The results on this page are produced by running elastiknn on the ann-benchmarks benchmark on an AWS EC2 r6i.xlarge instance.
We use `task dockerStartBenchmarkingCluster` to start a single-node benchmarking cluster `task annbRunLocalFashionMnist` to run the benchmark.

See the ann-benchmarks directory in the Elastiknn repository for more details.

## Results

### Fashion MNIST

<img src="data:image/png;base64, {% include_relative performance/fashion-mnist/plot.b64 %}" width="600px" height="auto"/> 

{% include_relative performance/fashion-mnist/results.md %}

## Comparison to Other Methods

If you need high-throughput nearest neighbor search for batch jobs, there are many faster methods.
When comparing Elastiknn performance to these methods, consider the following:

1. Elastiknn executes entirely in the Elasticsearch JVM and is implemented with existing Elasticsearch and Lucene primitives.
   Many other methods use C and C++, which are generally faster than the JVM for pure number crunching tasks.
2. Elastiknn issues an HTTP request for _every_ query, since a KNN query is just a standard Elasticsearch query.
   Most other methods operate without network I/O.
3. Elastiknn stores vectors on disk and uses zero caching beyond the caching that Lucene already implements.
   Most other methods operate entirely in memory.
4. Elastiknn scales horizontally out-of-the-box by adding shards to an Elasticsearch index.
   Query latency typically scales inversely with the number of shards, i.e., queries on an index with two shards will be 2x faster than an index with one shard.
   Few other methods are this simple to parallelize.

