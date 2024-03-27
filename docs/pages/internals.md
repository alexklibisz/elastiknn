---
layout: single
title: Internals
description: "Internals"
permalink: /internals/
toc: true
toc_label: Contents
toc_icon: cog
---

This document contains some details about Elastiknn internals which might be useful for users.

### Storing Model Parameters

The LSH models all use randomized parameters to hash vectors.
The simplest example is the bit-sampling model for Hamming similarity, parameterized by a list of randomly sampled indices.
A more complicated example is the stable distributions model for L2 similarity, parameterized by a set of random unit vectors
and a set of random bias scalars.
These parameters aren't actually stored anywhere in Elasticsearch.
Rather, they are lazily re-computed from a fixed random seed (0) each time they are needed.
The advantage of this is that it avoids storing and synchronizing potentially large parameter blobs in the cluster.
The disadvantage is that it's expensive to re-compute the randomized parameters.
So instead we keep a cache of models in each Elasticsearch node, keyed on the model hyperparameters (e.g. `L`, `k`, etc.).
The hyperparameters are stored inside the mappings where they are originally defined.

### Transforming and Indexing Vectors

Each vector is transformed (e.g. hashed) based on its mapping when the user makes an indexing request.
All vectors store a binary [doc values field](https://www.elastic.co/guide/en/elasticsearch/reference/current/doc-values.html)
containing a serialized version of the vector for exact queries, and vectors indexed using an LSH model index the hashes
using a Lucene Term field.
For example, for a sparse bool vector with a Jaccard LSH mapping, Elastiknn indexes the exact vector as a byte array in
a doc values field and the vector's hash values as a set of Lucene Terms.
