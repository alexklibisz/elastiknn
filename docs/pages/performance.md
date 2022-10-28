---
layout: single
title: Performance
description: "Elastiknn Performance"
permalink: /performance/
classes: wide
---

> But is it web-scale?

## Method

The results on this page are produced by running the following on a [2018 Mac Mini](https://support.apple.com/kb/SP782?locale=en_US) (6-Core i7, 64GB RAM variant):

1. Start a single-node Elastiknn cluster in a Docker container with a 12000m memory limit and 2 CPU limit. The exact configuration is in elastiknn/docker/docker-compose.benchmarking.yaml.
2. Run the Elastiknn variant of the [ann-benchmarks](https://github.com/erikbern/ann-benchmarks) client, with the `--local` flag, so it uses the local cluster. The exact commands are in elastiknn/Taskfile.yaml.
3. Parse the results with a Python script that converts them to some nice Markdown tables, embedded below.

**Caveats**

* This is a single-node benchmark, so latency from cluster communication is eliminated.
* The client and server run on the same host, so latency from client/server communication is minimized.

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

