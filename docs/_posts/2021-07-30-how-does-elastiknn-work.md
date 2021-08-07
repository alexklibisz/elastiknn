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
The best way to get _more_ detail is to explore and contribute to the project, hosted on [Github](https://github.com/alexklibisz/elastiknn).

This post should (ideally) convey that Elastiknn is just a combination of a few simple concepts, with a couple interesting optimizations for better performance.

This post assumes some familiarity with nearest neighbor search (aka "vector search"),[^vector-search-resources] 
Locality Sensitive Hashing,[^lsh-resources]
and Elasticsearch and Lucene.[^elasticsearch-resources]
  
This post does not directly cover the Elastiknn API. If that's of interest, I recommend reading the [API docs](/api) and the tutorial for [Multimodal Search on the Amazon Products Dataset](http://localhost:4000/tutorials/multimodal-search-amazon-products-dataset/).

## What does Elastiknn do?

In short, Elastiknn is an Elasticsearch plugin for exact and approximate nearest neighbor search.
The name is a combination of _Elastic_ and _KNN_ -- I pronounce it _elasti-knn_.

The full list of features (copied from the home page) is as follows:

- Datatypes to efficiently store dense and sparse numerical vectors in Elasticsearch documents, including multiple vectors per document.
- Exact nearest neighbor queries for five similarity functions: [L1](https://en.wikipedia.org/wiki/Taxicab_geometry), [L2](https://en.wikipedia.org/wiki/Euclidean_distance), [Cosine](https://en.wikipedia.org/wiki/Cosine_similarity), [Jaccard](https://en.wikipedia.org/wiki/Jaccard_index), and [Hamming](https://en.wikipedia.org/wiki/Hamming_distance).
- Approximate queries using [Locality Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) for L2, Cosine, Jaccard, and Hamming similarity.
- Integration of nearest neighbor queries with standard Elasticsearch queries.
- Incremental index updates: start with any number of vectors and incrementally create/update/delete more without ever re-building the entire index.
- Implementation based on standard Elasticsearch and Lucene primitives, entirely in the JVM. Indexing and querying scale horizontally with Elasticsearch.

The API is embedded into the standard Elasticsearch JSON-over-HTTP API -- [see the API docs](/api).

There are language-specific HTTP clients for Python, Java, and Scala.
There are also Java libraries exposing just the similarity models and Lucene abstractions for standalone use.
The clients and libraries are [documented on the Libraries page](/libraries).

## Software Structure

### Language

The Elastiknn plugin implementation is about 60% Scala and 40% Java.
Scala is a JVM-based language, meaning you can call Java code from Scala, call Scala code from Java (with some limitations), and provide Scala and Java sources in a single JAR.
I find Scala to be a more expressive and productive language,[^scala-example-1] however its abstractions are not always free.
I use Scala in all the places where the need for productive abstraction and type-safety exceeds the need for speed.[^scala-example-2]
I use Java to implement the CPU-bound similarity models and Lucene queries.[^note-scala-java]

### Elasticsearch Plugins

Elasticsearch's documentation for plugin authors is pretty minimal -- it's definitely an expert-level feature.
However, there are plenty of examples to learn from. Most of the Elasticsearch datatypes and queries are actually implemented as plugins under the hood.
Some open-source plugins also serve as nice examples, e.g., [ingest-langdetect](https://github.com/spinscale/elasticsearch-ingest-langdetect) and
[read-only-rest](https://github.com/sscarduzio/elasticsearch-readonlyrest-plugin).

Elastiknn follows roughly the same structure as any Elasticsearch plugin that provides new datatypes and new queries.
For example, Elasticsearch's support for [Geo queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/geo-queries.html) is implemented as a [plugin](https://github.com/elastic/elasticsearch/blob/0b032faf71fa8a470a2493e713524fd588b368c2/modules/geo/src/main/java/org/elasticsearch/geo/GeoPlugin.java#L19) with an analagous structure. 
Instead of datatypes like `geo_point` and `geo_shape`, Elastiknn has `elastiknn_dense_float_vector` and `elastiknn_sparse_bool_vector`.
Instead of queries like `geo_bounding_box` and `geo_distance`, Elastiknn has `elastiknn_nearest_neighbors`.

The Elasticsearch plugin sources are compiled and packaged as a zip file (pretty much a JAR), to be installed via `elasticsearch-plugin` CLI.
You can run this installation command on each node at startup, or simply build it into a Docker image.

Finally, Elasticsearch plugins need to be re-built and re-released for every specific release of Elasticsearch.
This is because Elasticsearch only maintains backwards-compatibility at the HTTP API level.
The Java internals usually have a few minor breaking changes for every release.
This makes it difficult to backport bug-fixes and new features to older versions of the plugin.
Elastiknn has so far taken the approach that bug-fixes and new features are released only for the latest version of Elasticsearch.[^gradle]

### Diagram Overview

This diagram should help understand the components of Elastiknn and how they interact with Elasticsearch and Lucene.

The diagram is partitioned into rows and columns.
The rows delineate three use-cases, from top to bottom: specifying a mapping that includes a vector, indexing a new document according to this mapping, 
and executing a nearest neighbor query against the indexed vectors.
The columns delineate the systems involved, from left to right: the user, the Elasticsearch HTTP server, Elastiknn, and the Elasticsearch interface to Lucene.

Without further ado:

<img src="/assets/images/how-does-elastiknn-work-00.jpg" alt="Diagram overview of Elastiknn"/>

The main takeaway from this diagram should be that Elastiknn has three responsibilities:

1. `TypeParser` parses a JSON mapping into a `TypeMapper`
2. `TypeMapper` converts a vector into Lucene fields, which are indexed by Lucene
3. `KnnQueryBuilder` converts the query vector into a Lucene query, executed by Lucene

Elasticsearch and Lucene handle everything else: serving requests, indexing documents, and executing queries.

The most important and interesting parts of Elastiknn are the hashing models and the custom `MatchHashesAndScoreQuery`.
The hashing models are used in the `TypeMapper` and `KnnQueryBuilder` to convert vectors into Lucene Fields.
The `MatchHashesAndScoreQuery` executes a very simple (but carefully-optimized) term-based document retrieval and re-ranking.

I'll talk more about both of these in the upcoming sections.

## Hashing Models

As a quick reminder, the idea of Locality Sensitive Hashing is that we can approximate exact similarity of vectors by converting them into discrete hashes,
with the key property that the probability of a hash collision for any pair of vectors correlates to their exact similarity.

This model is particularly compelling with respect to Lucene and Elasticsearch: it allows us to store and search vector hashes just like words in a standard text document.
In fact, there is absolutely zero difference in how Lucene handles vector hashes compared to plain old words. 
Both are serialized as byte arrays and used as keys in an inverted index.

With that in mind, this section will cover five of Elastiknn's hashing models.
The models differ in their specific implementation, generally based on the similarity function they approximate, but they all share the same interface:

> take a vector as input and produce a set of hashes as output.

The `HashingModel` interface should convey this concisely. Hashes can be repeated for some models, hence the `HashAndFreq` type.

```java
package com.klibisz.elastiknn.models;

public class HashingModel {
    public interface SparseBool {
        HashAndFreq[] hash(int[] trueIndices, int totalIndices);
    }
    public interface DenseFloat {
        HashAndFreq[] hash(float[] values);
    }
}

// ...

// A hash represented as a byte array and the frequency with which this hash occurred.
public class HashAndFreq implements Comparable<HashAndFreq> {
    public final byte[] hash;
    public final int freq;
    
    // Typical POJO boilerplate omitted.
}
```

Without further ado, let's have a look at each of the hashing models.

### L2 LSH

### Cosine LSH

### Permutation LSH

### Jaccard LSH

### Hamming LSH


## MatchHashesAndScoreQuery

<!--

## Indexing and Searching Strategies

### Exact Nearest Neighbors

### Approximate Nearest Neighbors

#### Locality Sensitive Hashing

#### Indexing

#### Searching

## Combining Nearest Neighbors with Elasticsearch Queries

## Locality Sensitive Hashing Algorithms

## Interesting Optimizations

### Fast Vector Serialization

### Custom Lucene Query
--> 
---

<!--Footnotes-->

[^vector-search-resources]: For a brief introduction to vector search aimed at Elastiknn users, start watching [this presentation at 1m56s](https://youtu.be/M4vqhmSZMTI?t=116). For a more general introduction, see [this Medium article about the Billion-Scale Approximate Nearest Neighbor Search Challenge](https://medium.com/big-ann-benchmarks/neurips-2021-announcement-the-billion-scale-approximate-nearest-neighbor-search-challenge-72858f768f69).
[^lsh-resources]: For a thorough textbook introduction to Locality Sensitive Hashing, including a bit of math, read Chapter 3 of [Mining of Massive Datasets](http://www.mmds.org/). For a thorough video lecture introduction, watch lectures 13 through 20 of the [Scalable Data Science course from IIT Kharagpur](https://www.youtube.com/watch?v=06HGoXE6GAs&list=PLbRMhDVUMngekIHyLt8b_3jQR7C0KUCul&index=14). 
[^elasticsearch-resources]: The short story is: Lucene is a search library implemented in Java, generally intended for text search. You give Lucene some documents, and it parses the terms and stores an inverted index. You give it some terms, and it uses the inverted index to tell you which documents contain those terms. Elasticsearch is basically a distributed cluster of Lucene indices with a JSON API.
[^scala-example-1]: Consider the difference between representing different query types as [Scala case classes](https://github.com/alexklibisz/elastiknn/blob/dcabb8cbf6d793fe83ac85a2a6ffa91786f87c73/elastiknn-api4s/src/main/scala/com/klibisz/elastiknn/api/package.scala#L126-L167) vs. [Java POJOs](https://github.com/alexklibisz/elastiknn/blob/dcabb8cbf6d793fe83ac85a2a6ffa91786f87c73/elastiknn-client-java/src/main/java/com/klibisz/elastiknn/api4j/ElastiknnNearestNeighborsQuery.java#L12-L158).
[^scala-example-2]: For example, [this pattern rewrites a user-provided query and an existing index mapping into an internal Elasticsearch query](https://github.com/alexklibisz/elastiknn/blob/6798e3b4e4c08a1ac14e9be668761fc284bbacaa/elastiknn-plugin/src/main/scala/com/klibisz/elastiknn/query/ElastiknnQuery.scala#L52-L112). 
[^note-scala-java]: When deciding to use Java or Scala, I find it useful to ask if I'm optimizing "in the large" or "in the small." For more context, read the first couple sections of [Daniel Spiewak](https://twitter.com/djspiewak)'s post [Why are Fibers Fast?](https://typelevel.org/blog/2021/02/21/fibers-fast-mkay.html).
[^gradle]: The only alternative seems to be maintaining a separate copy of the code for every version of Elasticsearch. This seems to be the approach taken by [read-only-rest](https://github.com/sscarduzio/elasticsearch-readonlyrest-plugin).
  



<!--

The Elasticsearch node starts and loads the Elastiknn JAR, which includes two [`TypeParsers`](https://github.com/alexklibisz/elastiknn/blob/e593289700af073c0ea89713fc1d8ed2a59f3c2c/elastiknn-plugin/src/main/scala/com/klibisz/elastiknn/mapper/VectorMapper.scala#L106-L114),
one for the `elastiknn_dense_float_vector`, one for the `elastiknn_sparse_bool_vector` type.

The user provides a JSON mapping via HTTP POST or PUT, including one or more fields of type `elastiknn_dense_float_vector` or `elastiknn_sparse_bool_vector`.

The Elasticsearch node receives this mapping, parses out the `elastiknn_*_vector` sections of JSON, and hands the JSON to the corresponding `TypeParser`. 

The `TypeParser` parses the JSON and returns an instance of [`Mapper.Builder`](https://github.com/elastic/elasticsearch/blob/1098737fe73d4bdfe47b8ebd397533a7b9d7f30b/server/src/main/java/org/elasticsearch/index/mapper/Mapper.java#L18). This instance contains enough information to parse and index new vectors according to the user-specified mapping. 
For example, if the mapping is:
```json
"my_vec": {
    "type": "elastiknn_dense_float_vector",
    "elastiknn": {
        "dims": 100,
        "model": "lsh",
        "similarity": "cosine",
        "L": 99,
        "k": 1
    }
}
```
then the `Mapper.Builder` keeps track of the vector dimensions, the model type, the similarity, and the two LSH parameters (`L` and `k`).
Anytime a node reboots, the `TypeParser` is able to recreate the same `Mapper.Builder` as a function of the JSON mapping.

The `Mapper.Builder`'s main job is to return a [`FieldMapper`](https://github.com/alexklibisz/elastiknn/blob/e593289700af073c0ea89713fc1d8ed2a59f3c2c/elastiknn-plugin/src/main/scala/com/klibisz/elastiknn/mapper/VectorMapper.scala#L151-L199),
which can be used to take a document a produce a set of [Lucene `IndexableFields`](https://lucene.apache.org/core/8_8_2/core/org/apache/lucene/index/IndexableField.html).

The point is: Elastiknn tells Elasticsearch how to take a JSON mapping and create an object that can take a JSON document and return set of indexable Lucene fields. 

Let's go back to the user.

The user POSTs or PUTs a JSON document containing a vector.

Elasticsearch parses out the `elastiknn_*_vector` fields. For each of them, Elasticsearch passes the JSON to the field's corresponding `FieldMapper`s. 
The `FieldMapper` parses the JSON and returns a set of Lucene fields.     

-->