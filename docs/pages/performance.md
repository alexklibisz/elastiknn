---
layout: default
title: Performance
nav_order: 5
description: "Elastiknn Performance"
permalink: /performance/
---

# Performance
{: .no_toc }

Elastiknn is benchmarked on a subset of datasets from the popular [ann-benchmarks project](https://github.com/erikbern/ann-benchmarks).

**Method**

For each dataset, run a grid-search over mappings and compatible queries.
Report the pareto frontier for recall and queries/second.
Present the mapping and query used for each point on the pareto frontier.
Partition these results by the number of shards in the index, which controls parallelism at query time.
Currently, all datasets are indexed with exactly one shard (i.e. no parallelism), merged into a single segment, with zero replicas.

Each run uses a single-node Elasticsearch cluster with an 8GB heap, G1 garbage collection, tmpfs storage (for faster 
indexing, has minimal effect on queries), running on C5.4XLarge EC2 instances in an AWS EKS cluster.

**Work In Progress**

1. Results for the remaining ann-benchmarks datasets.
2. Results for multiple shards and possibly multiple nodes (i.e. quantify effects of scaling out).
3. Results for larger datasets, e.g. [Deep1B](http://sites.skoltech.ru/compvision/noimi/) and [Amazon reviews image vectors](http://jmcauley.ucsd.edu/data/amazon/links.html).

**Caveats**

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

--- 

## Results
{: .no_toc }

1. TOC
{:toc}

<!-- 
Everything below is generated using a python program 
python3 report.py aggregate.csv > ../../docs/pages/performance-raw.md
-->

{% include_relative performance-raw.md %}
