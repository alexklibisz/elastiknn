---
layout: single
title: "How Does Elastiknn Work?"
permalink: /posts/how-does-elastiknn-work-july-2021/
date: 2021-07-24 18:00:00 -0500
author_profile: true
author: Alex Klibisz
excerpt: "This post walks through the implementation of Elastiknn. If I've done my job well, you should find it's pretty simple."
classes: wide
---

This post covers the implementation of Elastiknn in fairly thorough detail. 
The only way to get _more_ detail is to read and modify the code on [Github](https://github.com/alexklibisz/elastiknn), which I also highly recommend.

Hopefully I can convey that this implementation is just a combination of a few simple concepts, with only a couple non-trivial optimizations for better performance.

This post assumes familiarity with nearest neighbor search (aka "vector search").
If you need a refresher on this topic, there are a number of existing resources.[^ann-resources]
  
This post does not directly cover the Elastiknn API. If that's of interest, I recommend reading the [Multimodal Search on the Amazon Products Dataset tutorial](http://localhost:4000/tutorials/multimodal-search-amazon-products-dataset/)
and then skimming the [API docs](/api) as a reference for different algorithms supported by Elastiknn.

## What does Elastiknn do?

In short, Elastiknn is an Elasticsearch plugin for exact and approximate nearest neighbor search.
The fill list of features (copied from the home page) is as follows:

- Datatypes to efficiently store dense and sparse numerical vectors in Elasticsearch documents, including multiple vectors per document.
- Exact nearest neighbor queries for five similarity functions: [L1](https://en.wikipedia.org/wiki/Taxicab_geometry), [L2](https://en.wikipedia.org/wiki/Euclidean_distance), [Cosine](https://en.wikipedia.org/wiki/Cosine_similarity), [Jaccard](https://en.wikipedia.org/wiki/Jaccard_index), and [Hamming](https://en.wikipedia.org/wiki/Hamming_distance).
- Approximate queries using [Locality Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) and related algorithms for L2, Cosine, Jaccard, and Hamming similarity.
- Integration of nearest neighbor queries with standard Elasticsearch queries.
- Incremental index updates. Start with 1 vector or 1 million vectors and then create/update/delete documents and vectors without ever re-building the entire index.
- Implementation based on standard Elasticsearch and Lucene primitives, entirely in the JVM. This means deployment is a simple plugin installation and indexing and querying both scale horizontally with Elasticsearch.

The primary API is embedded into the standard Elasticsearch JSON-over-HTTP API, [documented on the API page](/api).

There are language-specific HTTP clients for Python, Java, and Scala, as well as Java libraries exposing the similarity models and Lucene abstractions for standalone use.
The clients and libraries are [documented on the Libraries page](/libraries).

## Software Structure

This section describes the general software structure of the Elastiknn plugin.

### Language

The Elastiknn codebase is about 60% Scala and 40% Java.
Scala is a JVM-based language, meaning you can call Java code from Scala, call Scala code from Java (with some limitations), and bundle Scala and Java sources in a single JAR.
I find Scala to be a more expressive and productive language,[^scala-example-1] however its abstractions are not always free.
I use Scala in all the places where the need for productive abstraction and type-safety exceeds the need for speed.[^scala-example-2]
I use Java to implement the CPU-bound similarity models and Lucene queries.[^note-scala-java]

### Elasticsearch Plugins

Elasticsearch's documentation for plugin authors is understandably pretty sparse -- it's definitely an expert-level feature.
However, there are plenty of examples to learn from. Most of the Elasticsearch datatypes and queries are actually implemented as plugins under the hood.
Some open-source plugins also serve as nice examples, e.g., [ingest-langdetect](https://github.com/spinscale/elasticsearch-ingest-langdetect) and
[read-only-rest](https://github.com/sscarduzio/elasticsearch-readonlyrest-plugin).

Elastiknn follows roughly the same structure as any Elasticsearch plugin that provides new datatypes and new queries.
For example, Elasticsearch's support for [Geo queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/geo-queries.html) is implemented as a plugin with an analagous structure. 
Instead of datatypes like `geo_point` and `geo_shape`, Elastiknn has `elastiknn_dense_float_vector` and `elastiknn_sparse_bool_vector`.
Instead of queries like `geo_bounding_box` and `geo_distance`, Elastiknn has `elastiknn_nearest_neighbors`.

The Elasticsearch plugin sources are compiled and packaged as a zip file (pretty much a JAR), to be installed via `elasticsearch-plugin` CLI.
You can run this installation command on each node at startup, or simply build it into a Docker image.

### Important Flows in the Plugin

Let's look at the three most important flows through the Elastiknn plugin: specifying a mapping, indexing a new document containing a vector, and executing a nearest neighbor query.

1. The user provides a JSON mapping via HTTP POST or PUT. This mapping includes one or more fields with type `elastiknn_dense_float_vector` or `elastiknn_sparse_bool_vector`.
2. The Elasticsearch web server receives this mapping, parses out the `elastiknn_vector` sections of JSON, and hands them off to the Elastiknn [`TypeMapper`](https://github.com/alexklibisz/elastiknn/blob/e593289700af073c0ea89713fc1d8ed2a59f3c2c/elastiknn-plugin/src/main/scala/com/klibisz/elastiknn/mapper/VectorMapper.scala#L106-L114).
3. The TypeParser returns an instance of [`Mapper.Builder`](https://github.com/elastic/elasticsearch/blob/1098737fe73d4bdfe47b8ebd397533a7b9d7f30b/server/src/main/java/org/elasticsearch/index/mapper/Mapper.java#L18). This instance contains enough information to parse and index new vectors according to the user-specified mapping. For example, it knows the vector dimensions and LSH model parameters.


## Exact Nearest Neighbors

### Indexing

### Searching

## Approximate Nearest Neighbors

### Locality Sensitive Hashing

### Indexing

### Searching

## Combining Nearest Neighbors with Elasticsearch Queries

## Locality Sensitive Hashing Algorithms

### L2 LSH

### Cosine LSH

### Permutation LSH

### Jaccard LSH

### Hamming LSH

## Interesting Optimizations

### Fast Vector Serialization

### Custom Lucene Query

---

<!--Footnotes-->

[^ann-resources]: For a thorough textbook introduction (including a bit of math), read Chapter 3 of [Mining of Massive Datasets](http://www.mmds.org/). For a thorough video lecture introduction, watch lectures 13 through 20 of the [Scalable Data Science course from IIT Kharagpur](https://www.youtube.com/watch?v=06HGoXE6GAs&list=PLbRMhDVUMngekIHyLt8b_3jQR7C0KUCul&index=14). For an explanation in the context of large-scale ANN, read [this Medium article about the upcoming Billion-Scale Approximate Nearest Neighbor Search Challenge](https://medium.com/big-ann-benchmarks/neurips-2021-announcement-the-billion-scale-approximate-nearest-neighbor-search-challenge-72858f768f69). For a brief introduction aimed at Elastiknn users, start watching [this presentation at 1m56s](https://youtu.be/M4vqhmSZMTI?t=116).
[^scala-example-1]: Consider the difference between representing different query types as [Scala case classes](https://github.com/alexklibisz/elastiknn/blob/dcabb8cbf6d793fe83ac85a2a6ffa91786f87c73/elastiknn-api4s/src/main/scala/com/klibisz/elastiknn/api/package.scala#L126-L167) vs. [Java POJOs](https://github.com/alexklibisz/elastiknn/blob/dcabb8cbf6d793fe83ac85a2a6ffa91786f87c73/elastiknn-client-java/src/main/java/com/klibisz/elastiknn/api4j/ElastiknnNearestNeighborsQuery.java#L12-L158).
[^scala-example-2]: For example, [this pattern rewrites a user-provided query and an existing index mapping into an internal Elasticsearch query](https://github.com/alexklibisz/elastiknn/blob/6798e3b4e4c08a1ac14e9be668761fc284bbacaa/elastiknn-plugin/src/main/scala/com/klibisz/elastiknn/query/ElastiknnQuery.scala#L52-L112). 
[^note-scala-java]: When deciding to use Java or Scala, I find it useful to ask if I'm optimizing "in the large" or "in the small." For more context, read the first couple sections of [Daniel Spiewak](https://twitter.com/djspiewak)'s post [Why are Fibers Fast?](https://typelevel.org/blog/2021/02/21/fibers-fast-mkay.html).