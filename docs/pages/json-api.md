---
layout: default
title: REST API
nav_order: 3
description: "Elastiknn REST API"
permalink: /rest-api
---

# REST API
{: .no_toc }

This document covers the REST API for using Elastiknn.
Once you've [installed Elastiknn](/installation/), you can use the REST API just like you would use the [official Elasticsearch REST APIs](https://www.elastic.co/guide/en/elasticsearch/reference/current/rest-apis.html).

1. TOC
{:toc}

## Mappings

Before indexing vectors you must define a mapping specifying one of the two vector datatypes and a small handful of other properties. These determine how to store vectors for various kinds of searches.

The general structure of specifying a mapping looks like this:

```json
PUT /my-index/_mapping
{
  "properties": {                               # 1
    "my_vec": {                                 # 2 
      "type": "elastiknn_sparse_bool_vector",   # 3
      "elastiknn": {                            # 4
        "dims": 100,                            # 5
        "model": "sparse_indexed",              # 6
        "..." : "...",                          # 7
      }
    }
  }
}
```

|Property|Description|
|:--|:--|
|1|Dictionary of document fields, same as the [official PUT Mapping API](https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-put-mapping.html)|
|2|Name of the field containing your vector. This is arbitrary and can be nested under other fields.|
|3|Type of vector you want to store. See datatypes below.|
|4|Dictionary of elastiknn settings|
|5|Dimensionality of your vector. All vectors stored at this field (`my_vec`) must have the same dimensionality.|
|6|Model type. This and the model parameters will determine what kind of searches you can run. See more on models below.|
|7|Additional model parameters. See models below.|

### elastiknn_sparse_bool_vector Datatype

This type is optimized for vectors where each index is either `true` or `false` and the majority of indices are `false`. For example, you might represent a bag-of-words encoding of a document, where each index corresponds to a word in a vocabulary and any given document contains a very small fraction of all words. Internally, Elastiknn saves space by only storing a list of the true indices.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_sparse_bool_vector",  # 1
            "elastiknn": {
                "dims": 25000,                       # 2
                "..." : "...",                       # 3
            }
        }
    }
}
```

|Property|Description|
|:--|:--|
|1|The type name. Case sensitive.|
|2|Dimensionality of the vector. This is the total number of possible indices.|
|3|Aditional model parameters. See models below.|

### elastiknn_dense_float_vector Datatype

This type is optimized for vectors where each index is a floating point number, all of the indices are populated, and the dimensionality usually doesn't exceed ~1000. For example, you might store a word embedding or an image vector. Internally, Elastiknn uses Java Floats to store the values.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_dense_float_vector",  # 1
            "elastiknn": {
                "dims": 100,                         # 2
                "..." : "...",                       # 3
            }
        }
    }
}
```

|Property|Description|
|:--|:--|
|1|The type name. Case sensitive.|
|2|Dimensionality of the vector. This shouldn't exceed single-digit thousands. If it does, consider doing some sort of dimensionality reduction.|
|3|Aditional model parameters. See models below.|

### Exact Model

The exact model will allow you to run exact searches. These don't levarage any indexing constructs and have `O(n^2)` runtime, where `n` is the total number of documents.

You don't need to supply any `"model": "..."` value or any model parameters to use this model.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_(dense_float | sparse_bool)_vector",  # 1
            "elastiknn": {
                "dims": 100,                                         # 2
            }
        }
    }
}
```

|Property|Description|
|:--|:--|
|1|Vector datatype. Both dense float and sparse bool are supported|
|2|Vector dimensionality. Always required.|

### Sparse Indexed Model

The sparse indexed model introduces an obvious optimization for exact queries on sparse bool vectors. Specifically, it indexes each of of true indices as a Lucene term, basically treating true indices like [Elasticsearch keywords](https://www.elastic.co/guide/en/elasticsearch/reference/current/keyword.html). Jaccard and Hamming similarity both require computing the intersection of the query vector against all indexed vectors, and indexing the true indices makes this operation much more efficient. However, you must consider that there is an upper bound on the number of possible terms in a term query, [see the `index.max_terms_count` setting.](https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html#index-max-terms-count) If the number of true indices in your vectors exceeds this limit, you'll have to adjust it or experience query failures.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_sparse_bool_vector",  # 1
            "elastiknn": {
                "dims": 100,                         # 2
                "model": "sparse_indexed",           # 3
            }
        }
    }
}
```

|Property|Description|
|:--|:--|
|1|Vector datatype. Only sparse bool vectors are supported with this model.|
|2|Vector dimensionality. Always required.|
|3|Model type. Case sensitive.|

### Jaccard LSH Model

### Hamming LSH Model

### Angular LSH Model

### L1 LSH Model

## Vectors

### elastiknn_sparse_bool_vector

### elastiknn_dense_float_vector

## Queries

### Query Vector

### Exact Model

### Sparse Indexed Model

### Jaccard LSH Model

## Mapping and Query Compatibility