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

1. Datatypes to efficiently store floating-point and boolean vectors in Elasticsearch documents.
2. Exact nearest neighbor queries for five similarity functions: [L1](https://en.wikipedia.org/wiki/Taxicab_geometry), [L2](https://en.wikipedia.org/wiki/Euclidean_distance), [Angular](https://en.wikipedia.org/wiki/Cosine_similarity), [Jaccard](https://en.wikipedia.org/wiki/Jaccard_index), and [Hamming](https://en.wikipedia.org/wiki/Hamming_distance).
3. Approximate nearest neighbor queries using [Locality Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) for four similarity functions: L2, Angular, Jaccard, and Hamming.
4. Combine nearest neighbor queries with standard Elasticsearch queries.
5. Scales horizontally with Elasticsearch.

**Additional Features in Progress**

- Approximate queries using Multiprobe Locality Sensitive Hashing.

### Use Cases

1. Horizontally scalable nearest neighbor search
2. Visual similarity search
3. Text embedding search

### Caveats

Elastiknn is very much a work in progress. I appreciate any feedback over on the [Github repo](https://github.com/alexklibisz/elastiknn).