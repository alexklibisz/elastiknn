---
layout: single
title: Performance
description: "Elastiknn Performance"
permalink: /performance/
classes: wide
---

This document should answer the question "how fast is Elastiknn?"

Elastiknn is benchmarked on a subset of datasets from the popular [ann-benchmarks project](https://github.com/erikbern/ann-benchmarks).

Performance is evaluated as the tradeoff between recall and query throughput (queries / second).
There are three main contributing factors:

1. Cluster settings. Example, multiple shards in an index can be searched in parallel, so will generally increase throughput.
2. Mapping settings. These control how vectors get indexed. Example: more LSH tables increase recall and decrease throughput. 
3. Query settings. These control how queries get executed. Example: more candidates increase recall and decrease throughput. 

**Method**

For each dataset, evaluate several mapping and query settings on several cluster settings.
For each cluster and mapping settings, report the pareto frontier for recall and queries/second.
Plot the pareto curve, and report the mapping and query settings as a downloadable CSV.

The clusters are setup using the Elastic Kubernetes operator and run on EC2 C5.4XLarge instances.
The entire benchmark is orchestrated using Argo Workflows and can be found in the elastiknn-benchmarks directory of the repo.

**Work In Progress**

1. Results for the remaining ann-benchmarks datasets.
2. Results for larger datasets, e.g. [Deep1B](http://sites.skoltech.ru/compvision/noimi/) and [Amazon reviews image vectors](http://jmcauley.ucsd.edu/data/amazon/links.html).

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
   
## Results

<!-- 
Everything below is generated using a python program 
python3 report.py aggregate.csv > ../../docs/pages/performance-raw.md
-->

{% include_relative performance-raw.md %}
