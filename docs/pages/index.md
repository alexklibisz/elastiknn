---
layout: default
title: Home
nav_order: 1
description: "Elasticsearch Plugin for Nearest Neighbor Search"
permalink: /
---

# Elastiknn

## Elasticsearch Plugin for Nearest Neighbor Search

---

### Features

- Datatypes to efficiently store floating-point and boolean vectors in Elasticsearch documents.
- Exact nearest neighbor queries for five similarity functions: [L1](https://en.wikipedia.org/wiki/Taxicab_geometry), [L2](https://en.wikipedia.org/wiki/Euclidean_distance), [Angular](https://en.wikipedia.org/wiki/Cosine_similarity), [Jaccard](https://en.wikipedia.org/wiki/Jaccard_index), and [Hamming](https://en.wikipedia.org/wiki/Hamming_distance).
- Approximate nearest neighbor queries using [Locality Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) for four similarity functions: L2, Angular, Jaccard, and Hamming.
- Combine nearest neighbor queries with standard Elasticsearch queries.
- Scales horizontally with Elasticsearch. Everything is implemented using well-known Elasticsearch and Lucene constructs.

**Additional Features in Progress**

- Approximate queries for L1 similarity.
- Approximate queries using Multiprobe Locality Sensitive Hashing.
- Accessors for using Elastiknn vectors in Painless scripts.

### Use Cases

- Horizontally scalable nearest neighbor search
- Visual similarity search
- Word and document embedding search

### Caveats

Elastiknn is very much a work in progress. I appreciate any feedback over on the [Github repo](https://github.com/alexklibisz/elastiknn).