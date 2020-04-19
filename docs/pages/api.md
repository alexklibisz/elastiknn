---
layout: default
title: API
nav_order: 2
description: "Elastiknn API"
permalink: /api/
---

# Elastiknn API
{: .no_toc }

This document covers the Elastiknn API, including the REST API payloads and some important implementation details.

Once you've [installed Elastiknn](/installation/), you can use the REST API just like you would use the [official Elasticsearch REST APIs](https://www.elastic.co/guide/en/elasticsearch/reference/current/rest-apis.html).

1. TOC
{:toc}

## Mappings

Before indexing vectors you must define a mapping specifying one of two vector datatypes and a few other properties. These determine how vectors are indexed to support different kinds of searches.

The general structure for specifying a mapping looks like this:

```json
PUT /my-index/_mapping
{
  "properties": {                               # 1
    "my_vec": {                                 # 2 
      "type": "elastiknn_sparse_bool_vector",   # 3
      "elastiknn": {                            # 4
        "dims": 100,                            # 5
        "model": "sparse_indexed",              # 6
        ...                                     # 7
      }
    }
  }
}
```

|#|Description|
|:--|:--|
|1|Dictionary of document fields. Same as the [official PUT Mapping API.](https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-put-mapping.html)|
|2|Name of the field containing your vector. This is arbitrary and can be nested under other fields.|
|3|Type of vector you want to store. See datatypes below.|
|4|Dictionary of elastiknn settings.|
|5|Dimensionality of your vector. All vectors stored at this field (`my_vec`) must have the same dimensionality.|
|6|Model type. This and the model parameters will determine what kind of searches you can run. See more on models below.|
|7|Additional model parameters. See models below.|

### elastiknn_sparse_bool_vector Datatype

This type is optimized for vectors where each index is either `true` or `false` and the majority of indices are `false`. For example, you might represent a bag-of-words encoding of a document, where each index corresponds to a word in a vocabulary and any single document contains a very small fraction of all words. Internally, Elastiknn saves space by only storing a list of the true indices.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_sparse_bool_vector",  # 1
            "elastiknn": {
                "dims": 25000,                       # 2
                ...                                  # 3
            }
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Type name.|
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
                ...                                  # 3
            }
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Type name.|
|2|Dimensionality of the vector. This shouldn't exceed single-digit thousands. If it does, consider doing some sort of dimensionality reduction.|
|3|Aditional model parameters. See models below.|

### Exact Mapping

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

|#|Description|
|:--|:--|
|1|Vector datatype. Both dense float and sparse bool are supported|
|2|Vector dimensionality.|

### Sparse Indexed Mapping

The sparse indexed model introduces an obvious optimization for exact queries on sparse bool vectors. It indexes each of of true indices as a Lucene term, basically treating them like [Elasticsearch keywords](https://www.elastic.co/guide/en/elasticsearch/reference/current/keyword.html). Jaccard and Hamming similarity both require computing the intersection of the query vector against all indexed vectors, and indexing the true indices makes this operation much more efficient. However, you must consider that there is an upper bound on the number of possible terms in a term query, [see the `index.max_terms_count` setting.](https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html#index-max-terms-count) If the number of true indices in your vectors exceeds this limit, you'll have to adjust it or you'll encounter failed queries.

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

|#|Description|
|:--|:--|
|1|Vector datatype. Must be sparse bool vector.|
|2|Vector dimensionality.|
|3|Model type. This model has no additional parameters.|

### Jaccard LSH Mapping

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

|#|Description|
|:--|:--|
|1|Vector datatype. Must be sparse bool vector.|
|2|Vector dimensionality.|
|3|Model type.|
|4|Similarity.|
|5|Number of bands. Sometimes called the number of tables or `L`. Generally, increasing the number of bands increases [recall](https://en.wikipedia.org/wiki/Precision_and_recall#Recall) at the cost of additional compuation.|
|6|Number of rows per band. Sometimes called the number of hash functions (per table) or `k`. Generally, increasing the number of rows increases [precision](https://en.wikipedia.org/wiki/Precision_and_recall#Precision) at the cost of additional computation.|

### Hamming LSH Mapping

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

|#|Description|
|:--|:--|
|1|Vector datatype. Must be sparse bool vector.|
|2|Vector dimensionality.|
|3|Model type.|
|4|Similarity.|
|5|Number of bits (indices) to sample from each vector. This should not exceed the dimensionality. Generally, increasing the number of bits increases [recall.](https://en.wikipedia.org/wiki/Precision_and_recall#Recall)|

### Angular LSH Mapping

Enables approximate queries for the Angular similarity function on dense float vectors using the [Random Projection algorithm.](https://en.wikipedia.org/wiki/Locality-sensitive_hashing#Random_projection)

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

|#|Description|
|:--|:--|
|1|Vector datatype. Must be dense float vector.|
|2|Vector dimensionality.|
|3|Model type.|
|4|Similarity.|
|5|Number of bands. Sometimes called the number of tables or `L`. Generally, increasing the number of bands increases [recall](https://en.wikipedia.org/wiki/Precision_and_recall#Recall) at the cost of additional compuation.|
|6|Number of rows per band. Sometimes called the number of hash functions (per table) or `k`. Generally, increasing the number of rows increases [precision](https://en.wikipedia.org/wiki/Precision_and_recall#Precision) at the cost of additional computation.|

### L2 LSH Mapping

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
                "width": 3,                         # 7
            }
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Vector datatype. Must be dense float vector.|
|2|Vector dimensionality.|
|3|Model type.|
|4|Similarity.|
|5|Number of bands. Sometimes called the number of tables or `L`. Generally, increasing the number of bands increases [recall](https://en.wikipedia.org/wiki/Precision_and_recall#Recall) at the cost of additional compuation.|
|6|Number of rows per band. Sometimes called the number of hash functions (per table) or `k`. Generally, increasing the number of rows increases [precision](https://en.wikipedia.org/wiki/Precision_and_recall#Precision) at the cost of additional computation.|
|7|Integer bucket width. This determines how close two vectors have to be, when projected onto a third common vector, in order for the two vectors to fall in the same bucket. Typical values are low single-digit integers.|

## Vectors

You need to specify vectors in your REST requests when indexing documents containing a vector and when running queries with a literal query vector. In both cases you use the same JSON structure to define vectors. The examples below show the indexing case; the query case will be covered later.

### elastiknn_sparse_bool_vector

This assumes you've defined a mapping where `my_vec` has type `elastiknn_sparse_bool_vector`.

```json
POST /my-index/_doc
{
    "my_vec": {
       "true_indices": [1, 3, 5, ...],   # 1
       "total_indices": 100,             # 2
    }
}

```

|#|Description|
|:--|:--|
|1|JSON list of the indices which are `true` in your vector.|
|2|The total number of indices in your vector. This should match the `dims` in your mapping.|

### elastiknn_dense_float_vector

This assumes you've defined a mapping where `my_vec` has type `elastiknn_dense_float_vector`.

```json
POST /my-index/_doc
{
    "my_vec": {
        "values": [0.1, 0.2, 0.3, ...]    # 1
    }
}
```

|#|Description|
|:--|:--|
|1|JSON list of all floating point values in your vector. The length should match the `dims` in your mapping.|

## Nearest Neighbor Queries

Elastiknn provides a query called `elastiknn_nearest_neighbors`, which can be used in a `GET /_search` request just like standard Elasticsearch queries. 

The general query structure looks like this:

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {        # 1
            "field": "my_vec",                  # 2
            "vec": {                            # 3
                "values": [0.1, 0.2, 0.3, ...],               
            },
            "model": "exact",                   # 4
            "similarity": "angular",            # 5
            ...                                 # 6
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Query type provided by Elastiknn.|
|2|Name of the field containing your vectors.|
|3|Query vector. In this example it's a literal vector, but can also be a reference to an indexed vector.|
|4|Model type. Exact search used in this example. More models covered below.|
|5|One of the five similarity functions used to score indexed vectors.|
|6|Additional query parameters for different model/similarity combinations. Covered more below.|

### Datatypes and Similarities

Jaccard and Hamming similarity only work with sparse bool vectors. Angular, L1, and L2 similarity only work with dense float vectors. The following documentation assume this restriction is known.

These restrictions aren't inherent to the types and algorithms. They just seem uncommon and complicate the implementation, so they were left out initially.

### Similarity Scoring

Elasticsearch queries must return a non-negative floating-point score. For Elastiknn, the score for an indexed vector represents its similarity to the query vector. However, not all similarity functions increase as similarity increases. For example, a perfect similarity for the L1 and L2 functions is 0. Such functions really represent _distance_ without a well-defined mapping from distance to similarity. In these cases Elastiknn applies a transformation to invert the score such that more similar (_less distant_) vectors have higher scores. The exact transformations are described below.

|Similarity|Transformtion to Elasticsearch Score|Min Value|Max Value|
|:--|:--|:--|
|Jaccard|N/A|0|1.0|
|Hamming|N/A|0|1.0|
|Angular|`cosine similarity + 1`|0|2.0|
|L1|`1 / (l1 distance + 1e-6)`|0|1e6|
|L2|`1 / (l2 distance + 1e-6)`|0|1e6|

If you're using the `elastiknn_nearest_neighbors` query with other queries and the score values are inconvenient (e.g. huge values like 1e6), consider wrapping the query in a [Script Score Query](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html), where you can access and transform the `_score` value.

### Query Vector

The query vector is either a literal vector or a pointer to an indexed vector.

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {    
            ...
            "vec": {                                # 1
                "true_indices": [1, 3, 5, ...],
                "total_indices": 1000
            },
            ...
            "vec": {                                # 2
                "values": [0.1, 0.2, 0.3, ...]
            },
            ...
            "vec": {                                # 3
                "index": "my-other-index",
                "field": "my_vec",
                "id": "abc123"
            },
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Literal sparse bool query vector.|
|2|Literal dense float query vector.|
|3|Indexed query vector. This assumes you have another index called `my-other-index` with a document with id `abc123` that contains a valid vector in field `my_vec`.|

### Exact Query

Computes the exact similarity of a query vector against all indexed vectors. The algorithm is not efficient compared to approximate search, but the implementation has been extensively profiled and optimized.

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {        
            "field": "my_vec", 
            "vec": {                                # 1
                "values": [0.1, 0.2, 0.3, ...],
            },
            "model": "exact",                       # 2
            "similarity": "(angular | l1 | l2)",    # 3
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Query vector. Must match the datatype of `my_vec` or be a pointer to an indexed vector that matches the type.|
|2|Model name.|
|3|Similarity function. Must be compatible with the vector type.|

### Sparse Indexed Query

Computes the exact similarity of sparse bool vectors using a Lucene Boolean Query to compute the size of the intersection of true indices in the query vector against true indices in the indexed vectors.

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {        
            "field": "my_vec",                      # 1
            "vec": {                                # 2
                "true_indices": [1, 3, 5, ...],
                "total_indices": 100
            },
            "model": "sparse_indexed",              # 3
            "similarity": "(jaccard | hamming)",    # 4
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Indexed field. Must use `sparse_indexed` mapping model.|
|2|Query vector. Must be literal sparse bool or a pointer to an indexed sparse bool vector.|
|3|Model name.|
|4|Similarity function. Must be jaccard or hamming.|

### LSH Search Strategy

All of the LSH search models follow roughly the same strategy. They first retrieve approximate neighbors based on common hash terms and then compute the slower exact similarity for a small subset of the best approximate candidates. The exact steps are as follows:

1. Hash the query vector using model parameters that were specified in the indexed vector's mapping.
2. Convert the hash values to Lucene Terms in a Lucene Boolean Query which uses an inverted index to compute the size of the intersection of hash terms between the query vector and indexed vectors.
3. Vectors with non-empty intersections are passed to a scoring function which maintains a min-heap of size `candidates`. Notice `candidates` is a parameter for all LSH queries. The heap maintains the highest-scoring approximate neighbors. If an indexed vector exceeds the lowest approximate score in the heap, we compute its exact similarity and replace it in the heap. Otherwise it gets a score of 0.

If you set `candidates` to 0, the query skips all heap-related logic and exact similarity computations. The score for each vector is the number of intersecting hash terms.

It seems reasonable to set `candidates` to 2x to 10x larger than the number of hits you want to return (Elasticsearch defaults to 10 hits). For example, if you have 100,000 vectors in your index, you want the 10 nearest neighbors, and you set `candidates` to 100, then your query will compute the exact similarity roughly 100 times per shard.

### Jaccard LSH Query

Retrieve sparse bool vectors based on approximate Jaccard similarity.

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {
            "field": "my_vec",                     # 1
            "vec": {                               # 2
                "true_indices": [1, 3, 5, ...],
                "total_indices": 100
            },
            "model": "lsh",                        # 3
            "similarity": "jaccard",               # 4
            "candidates": 50,                      # 5
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Indexed field. Must use `lsh` mapping model with `jaccard` similarity.|
|2|Query vector. Must be literal sparse bool or a pointer to an indexed sparse bool vector.|
|3|Model name.|
|4|Similarity function.|
|5|Number of candidates. See the section on LSH Search Strategy.|

### Hamming LSH Query

Retrieve sparse bool vectors based on approximate Hamming similarity.

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {
            "field": "my_vec",                     # 1
            "vec": {                               # 2
                "true_indices": [1, 3, 5, ...],
                "total_indices": 100
            },
            "model": "lsh",                        # 3
            "similarity": "hamming",               # 4
            "candidates": 50,                      # 5
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Indexed field. Must use `lsh` mapping model with `hamming` similarity.|
|2|Query vector. Must be literal sparse bool or a pointer to an indexed sparse bool vector.|
|3|Model name.|
|4|Similarity function.|
|5|Number of candidates. See the section on LSH Search Strategy.|

### Angular LSH Query

Retrieve dense float vectors based on approximate Angular similarity.

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {
            "field": "my_vec",                          # 1
            "vec": {                                    # 2
                "true_indices": [0.1, 0.2, 0.3, ...],
                "total_indices": 100
            },
            "model": "lsh",                             # 3
            "similarity": "angular",                    # 4
            "candidates": 50,                           # 5
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Indexed field. Must use `lsh` mapping model with `angular` similarity.|
|2|Query vector. Must be literal dense float or a pointer to an indexed dense float vector.|
|3|Model name.|
|4|Similarity function.|
|5|Number of candidates. See the section on LSH Search Strategy.|

### L1 LSH Query

Work in progress.

### L2 LSH Query

Retrieve dense float vectors based on approximate L2 similarity.

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {
            "field": "my_vec",                          # 1
            "vec": {                                    # 2
                "true_indices": [0.1, 0.2, 0.3, ...],
                "total_indices": 100
            },
            "model": "lsh",                             # 3
            "similarity": "l2",                         # 4
            "candidates": 50,                           # 5
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Indexed field. Must use `lsh` mapping model with `l2` similarity.|
|2|Query vector. Must be literal dense float or a pointer to an indexed dense float vector.|
|3|Model name.|
|4|Similarity function.|
|5|Number of candidates. See the section on LSH Search Strategy.|

## Mapping and Query Compatibility

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


## Miscellaneous Implementation Details

Here are some other things worth knowing. Perhaps there will be a more cohesive way to present these in the future.

### Storing Model Parameters

The LSH models all use randomized parameters to hash vectors. The simplest example is the bit-sampling model for Hamming similarity, which is parameterized by a list of randomly sampled indices. A more complicated example is the stable distributions model for L2 similarity, which is parameterized by a set of random unit vectors and a set of random bias values. These parameters aren't actually stored anywhere in Elasticsearch. Rather, they are lazily re-computed from a fixed random seed each time they are needed. The advantage of this is that you don't have to worry about storing and synchronizing potentially large parameter documents somewhere in the cluster. The disadvantage is that it's expensive to re-compute the randomized parameters. So instead we keep an internal cache of models, keyed on the model hyperparameters (e.g. `bands`, `rows`, etc.). The hyperparameters are stored inside the mappings where they are originally defined.

### Transforming and Indexing Vectors

Each vector is transformed (e.g. hashed) based on its mapping when the user makes an indexing request. All vectors index a binary [doc values field](https://www.elastic.co/guide/en/elasticsearch/reference/current/doc-values.html) containing a serialized version of the vector, as well as term fields based on the vector's mapping. For example, for a sparse bool vector with a Jaccard LSH mapping, Elastiknn indexes the exact vector as a byte array in a doc values field and the vector's hash values as a set of Lucene Terms which point back to the document and field containing the vector. All of this transformation is part of the implementation for the `elastiknn_sparse_bool_vector` and `elastiknn_dense_float_vector` datatypes.

### Caching Mappings

When a user submits an `elastiknn_nearest_neighbors` query, Elastiknn has to retrieve the mapping for the indexed vector field in order to validate and hash the query vector. Mappings are typically static, so Elastiknn keeps an in-memory cache of mappings with a one minute expiration to avoid repeatedly requesting an unchanged mapping for every query. This cache is local to each Elasticsearch node.

The practical implication is that if you intend to delete and re-create an index with different Elastiknn mappings, you should wait more than 60 seconds between deleting and running new queries. In reality it usually takes much longer than one minute to delete, re-create, and populate an index.

### Caching Vectors

To compute exact similarities, Elastiknn has to retrieve the serialized version of each indexed vector, deserialize it, and instantiate a new object. Elasticsearch documents are typically static, especially while running high volumes of queries. So Elastiknn keeps an in-memory cache of deserialized vectors with a one minute expiration on each Elasticsearch node.

The practical implication is that you should wait more than one minute between updating vector contents and running queries that might access the modified vectors.

### Parallelism

From Elasticsearch's perspective, the `elastiknn_nearest_neighbors` query is no different than any other query. Elasticsearch receives a JSON query containing an `elastiknn_nearest_neighbors` key, passes the JSON to a parser implemented by Elastiknn, the parser produces a Lucene query, and Elasticsearch executes that query on each shard in the index. This means the simplest way to increase query parallelism is to add shards to your index. Obviously this has an upper limit, but the general performance implications of sharding are beyond the scope of this document.
