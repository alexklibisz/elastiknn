---
layout: default
title: Home
nav_order: 1
description: "Elasticsearch Plugin for Nearest Neighbor Search"
permalink: /
---

# Elastiknn

## Elasticsearch Plugin for Nearest Neighbor Search

Methods like word2vec and convolutional neural nets can convert many data modalities (text, images, users, items, etc.) into numerical vectors, such that pairwise distance computations on the vectors correspond to semantic similarity of the original data.
Elasticsearch is a ubiquitous search solution, but its support for vectors is limited.
This plugin fills the gap by bringing efficient exact and approximate vector search to Elasticsearch.
This enables users to combine traditional queries (e.g., "some product") with vector search queries (e.g., _an image (vector) of a product_) for an enhanced search experience.

---

## Features

- Datatypes to efficiently store dense and sparse numerical vectors in Elasticsearch documents.
- Exact nearest neighbor queries for five similarity functions: [L1](https://en.wikipedia.org/wiki/Taxicab_geometry), [L2](https://en.wikipedia.org/wiki/Euclidean_distance), [Angular](https://en.wikipedia.org/wiki/Cosine_similarity), [Jaccard](https://en.wikipedia.org/wiki/Jaccard_index), and [Hamming](https://en.wikipedia.org/wiki/Hamming_distance).
- Approximate queries using [Locality Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) and related algorithms for all similarities.
- Compose nearest neighbor queries with standard Elasticsearch queries.
- Incrementally build and update your index. Elastiknn doesn't perform any sort of model fitting, and a vector is just a field in a document. So you can start with 1 vector or 1 million and then create/update/delete documents and vectors without ever re-building the entire index.
- Implemented with standard Elasticsearch and Lucene primitives. Executes entirely in the Elasticsearch JVM. This means deployment is a simple plugin installation and indexing and querying both scale horizontally with Elasticsearch.

_Non-Features_

- If you need high-throughput nearest neighbor search for periodic batch jobs, there are several faster and simpler methods. [Ann-benchmarks](https://github.com/erikbern/ann-benchmarks) is a good place to find them.

---

## Community

- Post and discuss issues on [Github](https://github.com/alexklibisz/elastiknn) and [Gitter](https://gitter.im/elastiknn/community).
- Follow [@Elastiknn](http://twitter.com/elastiknn) for updates on releases.

