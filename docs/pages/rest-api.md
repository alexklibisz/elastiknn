---
layout: default
title: REST API
nav_order: 2
description: "Elastiknn REST API"
permalink: /rest-api/
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

The sparse indexed model introduces an obvious optimization for exact queries on sparse bool vectors. It indexes each of of true indices as a Lucene term, basically treating true indices like [Elasticsearch keywords](https://www.elastic.co/guide/en/elasticsearch/reference/current/keyword.html). Jaccard and Hamming similarity both require computing the intersection of the query vector against all indexed vectors, and indexing the true indices makes this operation much more efficient. However, you must consider that there is an upper bound on the number of possible terms in a term query, [see the `index.max_terms_count` setting.](https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html#index-max-terms-count) If the number of true indices in your vectors exceeds this limit, you'll have to adjust it or you'll encounter failed queries.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_sparse_bool_vector",  # 1
            "elastiknn": {
                "dims": 25000,                       # 2
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
|3|Model type. Case sensitive. This model has no additional parameters.|

### Jaccard LSH Model

The Jaccard LSH Model enables approximate queries for the Jaccard similarity function on sparse bool vectors using the [Minhash algorithm](https://en.wikipedia.org/wiki/MinHash).

The implementation is influenced by Chapter 3 of [Mining Massive Datasets.](http://www.mmds.org/) the [Spark MinHash implementation](https://spark.apache.org/docs/2.2.3/ml-features.html#minhash-for-jaccard-distance), the [tdebatty/java-LSH Github project](https://github.com/tdebatty/java-LSH), and the [Minhash for Dummies](http://matthewcasperson.blogspot.com/2013/11/minhash-for-dummies.html) blog post.

The total number of hash functions computed is `bands * rows`, and the total number indexed is `bands`.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_sparse_bool_vector", # 1
            "elastiknn": {
                "dims": 25000,                      # 2
                "model": "lsh",                     # 3
                "similarity": "jaccard",            # 4
                "bands": 99,                        # 5
                "rows": 1,                          # 6
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
|4|Similarity. Case sensitive.|
|5|Number of bands. Sometimes called the number of tables or `L`. Generally, increasing the number of bands will increase [recall](https://en.wikipedia.org/wiki/Precision_and_recall#Recall) at the cost of additional compuation.|
|6|Number of rows per band. Sometimes called the number of hash functions (per table) or `k`. Generally, increasing the number of rows will increase [precision](https://en.wikipedia.org/wiki/Precision_and_recall#Precision) at the cost of additional computation.|

### Hamming LSH Model

Enables approximate queries for the Hamming similarity function on sparse bool vectors using the [Bit-Sampling algorithm](http://mlwiki.org/index.php/Bit_Sampling_LSH).

The implementation is influenced by Chapter 3 of [Mining Massive Datasets.](http://www.mmds.org/)

The total number of hash functions computed and indexed for each vector is `bits`.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_sparse_bool_vector", # 1
            "elastiknn": {
                "dims": 25000,                      # 2
                "model": "lsh",                     # 3
                "similarity": "hamming",            # 4
                "bits": 99,                         # 5
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
|4|Similarity. Case sensitive.|
|5|Number of bits (indices) to sample from each vector. This should not exceed the dimensionality. Generally, increasing the number of bands will increase [recall](https://en.wikipedia.org/wiki/Precision_and_recall#Recall).|

### Angular LSH Model

Enables approximate queries for the Angular similarity function on dense float vectors using [Random Projection algorithm.](https://en.wikipedia.org/wiki/Locality-sensitive_hashing#Random_projection)

The implementation is influenced by Chapter 3 of [Mining Massive Datasets.](http://www.mmds.org/)

The total number of hash functions computed is `bands * rows`, and the total number indexed is `bands`.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_dense_float_vector", # 1
            "elastiknn": {
                "dims": 100,                        # 2
                "model": "lsh",                     # 3
                "similarity": "angular",            # 4
                "bands": 99,                        # 5
                "rows": 1,                          # 6
            }
        }
    }
}
```

|Property|Description|
|:--|:--|
|1|Vector datatype. Only dense float vectors are supported with this model.|
|2|Vector dimensionality. Always required.|
|3|Model type. Case sensitive.|
|4|Similarity. Case sensitive.|
|5|Number of bands. Sometimes called the number of tables or `L`. Generally, increasing the number of bands will increase [recall](https://en.wikipedia.org/wiki/Precision_and_recall#Recall) at the cost of additional compuation.|
|6|Number of rows per band. Sometimes called the number of hash functions (per table) or `k`. Generally, increasing the number of rows will increase [precision](https://en.wikipedia.org/wiki/Precision_and_recall#Precision) at the cost of additional computation.|

### L2 LSH Model

Enables approximate queries for the L2 (Euclidean) similarity function on dense float vectors using the [Stable Distributions method.](https://en.wikipedia.org/wiki/Locality-sensitive_hashing#Stable_distributions)

The implementation is influenced by Chapter 3 of [Mining Massive Datasets.](http://www.mmds.org/)

The total number of hash functions computed is `bands * rows`, and the total number indexed is `bands`.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_dense_float_vector", # 1
            "elastiknn": {
                "dims": 100,                        # 2
                "model": "lsh",                     # 3
                "similarity": "angular",            # 4
                "bands": 99,                        # 5
                "rows": 1,                          # 6
            }
        }
    }
}
```

|Property|Description|
|:--|:--|
|1|Vector datatype. Only dense float vectors are supported with this model.|
|2|Vector dimensionality. Always required.|
|3|Model type. Case sensitive.|
|4|Similarity. Case sensitive.|
|5|Number of bands. Sometimes called the number of tables or `L`. Generally, increasing the number of bands will increase [recall](https://en.wikipedia.org/wiki/Precision_and_recall#Recall) at the cost of additional compuation.|
|6|Number of rows per band. Sometimes called the number of hash functions (per table) or `k`. Generally, increasing the number of rows will increase [precision](https://en.wikipedia.org/wiki/Precision_and_recall#Precision) at the cost of additional computation.|

## Vectors

You need to specify vectors in your REST requests when indexing documents containing a vector and when running queries with a given query vector. In both cases you use the same JSON structure to define vectors. The examples below show the indexing case; the query case will be covered later.

### elastiknn_sparse_bool_vector

This assumes you've defined a mapping where `my_vec` has type `elastiknn_sparse_bool_vector`.

```json
POST /my-index/_doc
{
    "my_vec": {
       "true_indices": [1,3,42,88],     # 1
       "total_indices":  100,           # 2
    }
}

```

|Property|Description|
|:--|:--|
|1|A JSON list of the indices which are `true` in your vector.|
|2|The total number of indices in your vector. This should match the `dims` in your mapping.|

### elastiknn_dense_float_vector

This assumes you've defined a mapping where `my_vec` has type `elastiknn_dense_float_vector`.

```json
POST /my-index/_doc
{
    "my_vec": {
        "values": [0.1, 0.22, 0.333, 0.4444, 0.55555]    # 1
    }
}
```

|Property|Description|
|:--|:--|
|1|A JSON list of all floating point values in your vector. The length should match the `dims` in your mapping.|

## Queries

Elastiknn adds the `elastiknn_nearest_neighbors` query, which can be used in a `GET /my-index/_search` request just like standard Elasticsearch queries.

The general query structure looks like this:

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {

        }
    }
}

```

### Query Vector

### Exact Model

### Sparse Indexed Model

### Jaccard LSH Model

### Similarity Scoring

Talk about translating distance to similarity

## Model and Query Compatibility

Some models can be used for more than one type of query. For example, sparse bool vectors indexed with the Jaccard LSH model support exact searches using Jaccard and Hamming similarity. The opposite is _not_ true; vectors stored using the exact model do not support Jaccard LSH queries.

The tables below show valid model/query combinations. Rows are models and columns are queries. The similarity functions are abbreviated (J: Jaccard, H: Hamming, A: Angular, L1, L2).

### elastiknn_sparse_bool_vector

|Model / Query                  |Exact    |Sparse Indexed |Jaccard LSH |Hamming LSH |
|:--                            |:--      |:--            |:--         |:--         |
|Exact (i.e. no model specified)|✔ (J, H) |x              |x           |x           |
|Sparse Indexed                 |✔ (J, H) |✔ (J, H)       |x           |x           |
|Jaccard LSH                    |✔ (J, H) |x              |✔           |x           |
|Hamming LSH                    |✔ (J, H) |x              |x           |✔           |

### elastiknn_dense_float_vector

|Model / Query                   |Exact         |Angular LSH |L2 LSH |
|:--                             |:--           |:--         |:--    |
|Exact (i.e. no model specified) |✔ (A, L1, L2) |x           |x      |
|Angular LSH                     |✔ (A, L1, L2) |✔           |x      |
|L2 LSH                          |✔ (A, L1, L2) |x           |✔      |
