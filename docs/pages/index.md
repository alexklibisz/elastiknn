---
layout: default
title: Home
nav_order: 1
description: "Elasticsearch Plugin for Nearest Neighbor Search"
permalink: /
---

# Elastiknn

## Elasticsearch Plugin for Nearest Neighbor Search

Modern methods let us convert many data modalities (text, images, audio, users, items, etc.) into numerical vectors, such that pair-wise distance computations on the vectors correspond to semantic similarity of the original data.
Elasticsearch is a ubiquitous search solution, but its support for such vectors is limited.
The Elastiknn plugin fills that gap by bringing efficient exact and approximate vector search to Elasticsearch.
This enables users to combine traditional queries (e.g., "some product") with vector search queries (e.g., _an image (vector) of a product_).

---

## Features

- Datatypes to efficiently store floating-point and boolean vectors in Elasticsearch documents.
- Exact nearest neighbor queries for five similarity functions: [L1](https://en.wikipedia.org/wiki/Taxicab_geometry), [L2](https://en.wikipedia.org/wiki/Euclidean_distance), [Angular](https://en.wikipedia.org/wiki/Cosine_similarity), [Jaccard](https://en.wikipedia.org/wiki/Jaccard_index), and [Hamming](https://en.wikipedia.org/wiki/Hamming_distance).
- Approximate queries using [Locality Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) 
  and related algorithms for all five similarities.
- Compose nearest neighbor queries with standard Elasticsearch queries.
- Implemented with standard Elasticsearch and Lucene constructs and executes entirely in the Elasticsearch JVM. 
  This means deployment is a simple plugin installation and indexing and queries scale horizontally with Elasticsearch.
- Continuous updates and indexing. Since a vector is just a field in a document, you can create/update/delete documents and vectors without re-building the entire index.

_Non-Features_

- If you need nearest neighbor search for large batch jobs, there are several faster and faster methods. [Ann-benchmarks](https://github.com/erikbern/ann-benchmarks) is a good place to find them.

---

## Community

- Post and discuss issues on [Github](https://github.com/alexklibisz/elastiknn) and [Gitter](https://gitter.im/elastiknn/community).
- Follow [@Elastiknn](http://twitter.com/elastiknn) for updates on releases.

