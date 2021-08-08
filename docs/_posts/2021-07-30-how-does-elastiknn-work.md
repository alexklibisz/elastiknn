---
layout: single
title: "Tour de Elastiknn"
permalink: /posts/tour-de-elastiknn-august-2021/
date: 2021-08-07 18:00:00 -0500
author_profile: true
author: Alex Klibisz
excerpt: "This post is a tour through Elastiknn in fairly thorough detail."
classes: wide
mathjax: true
toc: true
toc_label: Table of Contents
toc_icon: cog
read_time: true
---

## Introduction

This post is a tour through Elastiknn in fairly thorough detail. 
The best way to get more detail is to explore and contribute to the project, hosted on [Github](https://github.com/alexklibisz/elastiknn).

This post should convey that Elastiknn is just a combination of a few simple concepts, with a couple interesting optimizations for improved performance.

This post assumes some familiarity with nearest neighbor search (i.e., _vector search_)[^vector-search-resources]
and Elasticsearch and Lucene.[^elasticsearch-resources]
Some knowledge of Locality Sensitive Hashing[^lsh-resources] is helpful but not strictly necessary.

This post does not directly cover the Elastiknn API. 
If that's of interest, please see the [API docs](/api) and the tutorial for [Multimodal Search on the Amazon Products Dataset](http://localhost:4000/tutorials/multimodal-search-amazon-products-dataset/).

The tour is structured as follows:
1. Cover Elastiknn's features at a high level.
2. Cover some software engineering details.
3. Examine a detailed component diagram to understand Elastiknn's interaction with Elasticsearch and Lucene.
4. Present Locality Sensitive Hashing (LSH) in enough detail to understand its implementation in Elastiknn.
5. Cover the specific implementation of LSH indexing and querying in Elastiknn.
6. Look at some open questions.
7. Finally, the appendix covers some more encyclopedic information, like brief summaries of the LSH implementations and some interesting optimizations.  

## What does Elastiknn do?

In short, Elastiknn is an Elasticsearch plugin for exact and approximate nearest neighbor search.
The name is a combination of _Elastic_ and _KNN_.

The full list of features (copied from the home page) is as follows:

- Datatypes to efficiently store dense and sparse numerical vectors in Elasticsearch documents, including multiple vectors per document.
- Exact nearest neighbor queries for five similarity functions: [L1](https://en.wikipedia.org/wiki/Taxicab_geometry), [L2](https://en.wikipedia.org/wiki/Euclidean_distance), [Cosine](https://en.wikipedia.org/wiki/Cosine_similarity), [Jaccard](https://en.wikipedia.org/wiki/Jaccard_index), and [Hamming](https://en.wikipedia.org/wiki/Hamming_distance).
- Approximate queries using [Locality Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) for L2, Cosine, Jaccard, and Hamming similarity.
- Integration of nearest neighbor queries with standard Elasticsearch queries.
- Incremental index updates: start with any number of vectors and incrementally create/update/delete more without ever re-building the entire index.
- Implementation based on standard Elasticsearch and Lucene primitives, entirely in the JVM. Indexing and querying scale horizontally with Elasticsearch.

The API is embedded into the Elasticsearch JSON-over-HTTP API -- [see the API docs](/api).

There are language-specific HTTP clients for Python, Java, and Scala.
There are also Java libraries exposing the LSH models and Lucene abstractions for standalone use.
The clients and libraries are [documented on the Libraries page](/libraries).

## Elastiknn Software Background

### Elasticsearch Plugins

Elasticsearch's documentation for plugin authors is pretty minimal -- it's definitely an expert-level feature.
However, there are plenty of examples to learn from. Most of the Elasticsearch datatypes and queries are actually implemented as plugins under the hood.
Some open-source plugins also serve as nice examples, e.g., [ingest-langdetect](https://github.com/spinscale/elasticsearch-ingest-langdetect) and
[read-only-rest](https://github.com/sscarduzio/elasticsearch-readonlyrest-plugin).

Elastiknn follows roughly the same structure as any Elasticsearch plugin that provides new datatypes and new queries.
For example, Elasticsearch's support for [Geo queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/geo-queries.html) is implemented as a [plugin](https://github.com/elastic/elasticsearch/blob/0b032faf71fa8a470a2493e713524fd588b368c2/modules/geo/src/main/java/org/elasticsearch/geo/GeoPlugin.java#L19) with an analagous structure. 
Instead of datatypes like `geo_point` and `geo_shape`, Elastiknn has `elastiknn_dense_float_vector` and `elastiknn_sparse_bool_vector`.
Instead of queries like `geo_bounding_box` and `geo_distance`, Elastiknn has `elastiknn_nearest_neighbors`.

The Elasticsearch plugin sources are compiled and packaged as a zip file (pretty much a JAR), to be installed by the `elasticsearch-plugin` CLI.
One common practice seems to be building an Elasticsearch Docker container which includes the plugin.

Finally, Elasticsearch plugins need to be re-built and re-released for every minor release of Elasticsearch.
This is because Elasticsearch only maintains backwards-compatibility at the HTTP API level.
The Java internals usually have a few minor breaking changes for every release (code changes and dependency updates). 
This makes it difficult to backport bug-fixes and new features to older versions of the plugin.
Elastiknn has so far taken the approach that bug-fixes and new features are released only for the latest version of Elasticsearch.[^gradle]

### Programming Languages

The Elastiknn implementation is about 60% Scala and 40% Java.
Scala is a JVM-based language, so we can call Java code from Scala, call Scala code from Java (with some limitations), and release Scala and Java sources as a single JAR.
Scala is generally a more expressive language, however its abstractions are not always free.
So Java is used for all the CPU-bound LSH models and Lucene abstractions, and Scala everywhere else.[^note-scala-java]

### Distance vs. Similarity

Elasticsearch requires non-negative relevance scores, with higher scores indicating higher relevance.

Elastiknn supports five vector similarity functions (L1, L2, Cosine, Jaccard, and Hamming).
Three of these are problematic with respect to this scoring requirement.

Specifically, L1 and L2 are generally defined as _distance_ functions, rather than similarity functions,
which means that higher relevance (i.e., lower distance) yields _lower_ scores.
Cosine similarity is defined over $$[-1, 1]$$, and we can't have negative scores.

To work around this, Elastiknn applies simple transformations to produce L1, L2, and Cosine _similarity_ in accordance with the Elasticsearch requirements.
The exact transformations are documented [on the API page](/api/#similarity-scoring).

## Elastiknn Component Diagram

This diagram should help understand the components of Elastiknn and how they interact with Elasticsearch and Lucene.

The diagram is partitioned into rows and columns.
The rows delineate three use-cases, from top to bottom: specifying a mapping that includes a vector, indexing a new document according to this mapping, 
and executing a nearest neighbor query against the indexed vectors.
The columns delineate the systems involved, from left to right: the user, the Elasticsearch HTTP server, Elastiknn, and the Elasticsearch interface to Lucene.

The main takeaway from this diagram should be that Elastiknn has three responsibilities:

1. `TypeParser` parses a JSON mapping into a `TypeMapper`
2. `TypeMapper` converts a vector into Lucene fields, which are indexed by Lucene
3. `KnnQueryBuilder` converts the query vector into a Lucene query, executed by Lucene

Elasticsearch and Lucene handle everything else: serving requests, indexing documents, executing queries, and aggregating results.

Without further ado:

<img src="/assets/images/how-does-elastiknn-work-00.jpg" alt="Diagram overview of Elastiknn"/>

The most important and interesting parts of Elastiknn are the LSH models and the custom `MatchHashesAndScoreQuery`.
The LSH models are used in the `TypeMapper` and `KnnQueryBuilder` to convert vectors into Lucene fields.
The `MatchHashesAndScoreQuery` executes a simple, carefully-optimized term-based document retrieval and re-ranking.

We'll look at both of these in detail in upcoming sections.

## Locality Sensitive Hashing Background

Before we look at Elastiknn's specific implementations of LSH, we should cover some LSH basics.
The notation is biased to expedite explanation with respect to Elastiknn.
There are additional resources which provide a more thorough, generalized presentation.[^lsh-resources]

### Hash Functions that Preserve Locality

The idea of Locality Sensitive Hashing is that we can approximate exact pairwise similarity of vectors by converting each vector into integer hash values,
with the key property that the probability of two vectors having the same hash value correlates to their exact similarity.

In other words, we define a hash function which preserves the locality of vectors.

Each similarity function uses a different hash function, but the general structure is identical:

> take a vector as input and produce an integer hash as output

More formally, a hash function $$h$$ for a $$d$$-dimensional real-valued vector $$v$$ is specified:

$$h(v): \mathbb{R}^d \rightarrow \mathbb{N}$$

And for boolean-valued vectors:

$$h(v): \{\text{true}, \text{false}\}^d \rightarrow \mathbb{N}$$

### Amplification

$$h(v)$$ produces a single integer hash value.

We generally need several of these values to generate enough collisions to retrieve results.

To account for this, we use a technique called _amplification_, which comes in two varieties: _AND_ amplification and _OR_ amplification.
These amplification techniques give us levers to control the [precision and recall](https://en.wikipedia.org/wiki/Precision_and_recall) of the retrieved results.

_AND_ amplification is the process of concatenating several distinct hash functions $$h_j(v)$$ to form a logical hash function $$g_i(v)$$.
The integer hyper-parameter $$k$$ is generally used to specify the number of concatenated functions.

$$g_i(v) = (h_{i k}(v), h_{i k + 1}(v), h_{i k + 2}(v) \ldots, h_{i k + k - 1}(v))$$

This is called _AND_ amplification because the $$h_j(v)$$ hashes are AND-ed together to form $$g_i$$.
As $$k$$ increases, the probability of collision decreases, which generally results in higher precision.

_OR_ amplification is the related process of computing multiple logical hash values $$g_i$$ for every vector.
The integer hyper-parameter $$L$$ is generally used to specify the number of logical hash values computed for every vector.

This is called _OR_ amplification because the $$g_i(v)$$ hashes are OR-ed in order to retrieve candidates for a query vector $$v$$.
Another way to think about this is that we simply have $$L$$ distinct hash tables, and each one uses a logical hash $$g_i(v)$$.
As $$L$$ increases, the probability of collision increases, which generally results in higher recall.

The number of operations to hash a vector and the size (i.e., number of bytes) of the resulting hash value are both a function of $$L \times k$$.
In other words, the cost of increased recall and precision is more CPU, memory, and storage.

To summarize:

- increasing $$L$$ generally increases recall
- increasing $$k$$ generally increases precision
- neither of these are free -- we pay in CPU, memory, and storage costs

### Multiprobe LSH

As described in the prior section, we can manipulate recall by adjusting $$L$$, but this comes at the cost of additional storage.

One clever technique to address this is Multiprobe LSH.[^cite-qin-2007]

The idea is that we can store fewer hashes in the index and instead compute additional hashes at query time
to increase the probability of retrieving relevant results.

This is analagous to query time synonym expansion in standard text retrieval: if our query includes _car_, then we might also consider looking for _auto_ and _vehicle_.

What are the synonyms of a hash value? This depends on the similarity function, but, in short, they are the adjacent hash buckets.
The original paper and most implementations of Multiprobe LSH are specific to L2 distance, but there's no inherent limitation to just this one distance.  

To be clear, Multiprobe LSH doesn't really eliminate any cost. 
Rather, it simply shifts the cost from CPU, memory, and storage at indexing time to CPU and memory at query time.
Depending on the application, this can be a worthwhile tradeoff.

## Locality Sensitive Hashing and Lucene

### Hashes and Words are the Same Thing

The LSH paradigm is particularly compelling with respect to Lucene: we can store and search vector hash values just like words in a standard text document.
In fact, there is absolutely zero difference in how Lucene handles vector hashes compared to words.
Both are serialized as byte arrays and used as keys in an inverted index.

The LSH models differ based on the similarity function they approximate, but they all share the same interface:

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

// A hash represented as a byte array and the frequency with which this hash occurred.
public class HashAndFreq implements Comparable<HashAndFreq> {
    public final byte[] hash;
    public final int freq;
    
    // Typical POJO boilerplate omitted.
}
```

### Naive Lucene Query: BooleanQuery

Blah

### Custom Lucene Query: MatchHashesAndScoreQuery

Blah

## Open Questions

Blah

## Conclusion

---

## Appendix

### L2 LSH Model

L2 (i.e., Euclidean) distance for two $$n$$-dimensional vectors $$u$$ and $$v$$ is defined as follows:

$$\text{dist}_{L2}(u, v) = \sqrt{\sum_{i=1}^n{(u_i - v_i)^2}}$$

The scoring convention in Elasticsearch is such that higher-relevance results have higher scores.
To satisfy this convention, Elastiknn converts L2 _distance_ into a _similarity_:

$$\text{sim}_{L2}(u, v) = \frac{1}{1 + d_{L2}(u, v)}$$

Elastiknn uses the _Stable Distributions_ method with multi-probe queries to approximate this similarity.[^cite-indyk-2006] [^cite-qin-2007]

Intuitively, this method hashes a vector $$v$$ by projecting it onto a randomly-generated vector $$A_i$$, adding a random offset $$B_i$$,
dividing the resulting scalar by a width parameter $$w$$, and finally flooring the scalar to represent a discretized section (or bucket) of $$A_i$$.

The process looks like this:

<img src="/assets/images/how-does-elastiknn-work-L2-lsh.jpg" alt="Visualization of L2 LSH hashing"/>

We can intuit that a vector close to $$v$$ would be likely to hash to the same section of $$A_i$$.

This hash function is defined more precisely as:

$$h_{i}(v) = \lfloor \frac{A_i \cdot v + B_i}{w} \rfloor$$

Each vector $$A_i$$ is sampled from a standard Normal distribution and each scalar $$B_i$$ is sampled from a uniform distribution in $$[0, 1]$$.
$$w$$ is provided by the user.

Key metrics:

- Number of hashes as a function of parameters: ...
- Hash size as a function of parameters: ...

### Cosine LSH Model

Blah

### Permutation LSH Model

Blah

### Jaccard LSH Model

Blah

### Hamming LSH Model

Blah

### Storing LSH Model Parameters

Each hashing model uses a set of parameters to hash vectors.

The simplest example is the bit-sampling model for Hamming similarity, parameterized by a list of randomly sampled indices.
A more complicated example is the stable distributions model for L2 similarity, parameterized by a set of random unit vectors
and a set of random bias scalars.

These parameters aren't actually stored anywhere in Elasticsearch.
Rather, they are lazily re-computed from a fixed random seed each time they are needed.

The advantage of this is that it avoids storing and synchronizing large parameter blobs in the cluster.
The disadvantage is that it's expensive to re-compute the randomized parameters.

Instead of re-computing them we keep a cache of models in each Elasticsearch node, keyed on the model hyper-parameters (`L`, `k`, etc.).
The hyper-parameters are stored inside the mappings where they are originally defined.

### Fast Vector Serialization

Blah

---

<!--Footnotes-->

[^vector-search-resources]: For a brief introduction to vector search aimed at Elastiknn users, start watching [this presentation at 1m56s](https://youtu.be/M4vqhmSZMTI?t=116). For a more general introduction, see [this Medium article about the Billion-Scale Approximate Nearest Neighbor Search Challenge](https://medium.com/big-ann-benchmarks/neurips-2021-announcement-the-billion-scale-approximate-nearest-neighbor-search-challenge-72858f768f69).
[^lsh-resources]: For a thorough textbook introduction to Locality Sensitive Hashing, including a bit of math, read Chapter 3 of [Mining of Massive Datasets](http://www.mmds.org/). For a thorough video lecture introduction, watch lectures 13 through 20 of the [Scalable Data Science course from IIT Kharagpur](https://www.youtube.com/watch?v=06HGoXE6GAs&list=PLbRMhDVUMngekIHyLt8b_3jQR7C0KUCul&index=14). 
[^elasticsearch-resources]: The short story is: Lucene is a search library implemented in Java, generally intended for text search. You give Lucene some documents, and it parses the terms and stores an inverted index. You give it some terms, and it uses the inverted index to tell you which documents contain those terms. Elasticsearch is basically a distributed cluster of Lucene indices with a JSON API. 
[^note-scala-java]: To support the claim of Scala being more expressive, consider the difference between representing different query types as [Scala case classes](https://github.com/alexklibisz/elastiknn/blob/dcabb8cbf6d793fe83ac85a2a6ffa91786f87c73/elastiknn-api4s/src/main/scala/com/klibisz/elastiknn/api/package.scala#L126-L167) vs. [Java POJOs](https://github.com/alexklibisz/elastiknn/blob/dcabb8cbf6d793fe83ac85a2a6ffa91786f87c73/elastiknn-client-java/src/main/java/com/klibisz/elastiknn/api4j/ElastiknnNearestNeighborsQuery.java#L12-L158). When deciding to use Java or Scala, I find it useful to ask if I'm optimizing "in the large" or "in the small." For more context, see [Daniel Spiewak's](https://twitter.com/djspiewak) post [Why are Fibers Fast?](https://typelevel.org/blog/2021/02/21/fibers-fast-mkay.html).
[^gradle]: The only alternative seems to be maintaining a separate copy of the code for every version of Elasticsearch. This seems to be the approach taken by [read-only-rest](https://github.com/sscarduzio/elasticsearch-readonlyrest-plugin).

[^cite-indyk-2006]: _Stable distributions, pseudorandom generators, embeddings, and data stream computation_ by Piotr Indyk, 2006
[^cite-qin-2007]: _Multi-Probe LSH: Efficient Indexing for High-Dimensional Similarity Search_ by Qin Lv, et. al., 2007