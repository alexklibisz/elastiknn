---
layout: splash
title: Home
description: "Elasticsearch Plugin for Nearest Neighbor Search"
permalink: /
---

## Elasticsearch Plugin for Nearest Neighbor Search

Methods like word2vec and convolutional neural nets can convert many data modalities (text, images, users, items, etc.) into numerical vectors, such that pairwise distance computations on the vectors correspond to semantic similarity of the original data.
Elasticsearch is a ubiquitous search solution, but its support for vectors is limited.
This plugin fills the gap by bringing efficient exact and approximate vector search to Elasticsearch.
This enables users to combine traditional queries (e.g., "some product") with vector search queries (e.g., _an image (vector) of a product_) for an enhanced search experience.

## Features

- Datatypes to efficiently store dense and sparse numerical vectors in Elasticsearch documents, including multiple vectors per document.
- Exact nearest neighbor queries for six similarity functions: [L1](https://en.wikipedia.org/wiki/Taxicab_geometry), [L2](https://en.wikipedia.org/wiki/Euclidean_distance), [Cosine](https://en.wikipedia.org/wiki/Cosine_similarity), [Dot](https://en.wikipedia.org/wiki/Dot_product) (for normalized vectors), [Jaccard](https://en.wikipedia.org/wiki/Jaccard_index), and [Hamming](https://en.wikipedia.org/wiki/Hamming_distance).
- Approximate queries using [Locality Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) for L2, Cosine, Dot, Jaccard, and Hamming similarity.
- Integration of nearest neighbor queries with standard Elasticsearch queries.
- Incremental index updates. Start with 1 vector or 1 million vectors and then create/update/delete documents and vectors without ever re-building the entire index.
- Implementation based on standard Elasticsearch and Lucene primitives, entirely in the JVM. Indexing and querying scale horizontally with Elasticsearch.

_Non-Features_

- If you need high-throughput nearest neighbor search for periodic batch jobs, there are several faster and simpler methods. [Ann-benchmarks](https://github.com/erikbern/ann-benchmarks) is a good place to find them.

## Community

- Post and discuss issues on [Github](https://github.com/alexklibisz/elastiknn).

