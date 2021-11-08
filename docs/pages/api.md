---
layout: single
title: API
description: "Elastiknn API"
permalink: /api/
toc: true
toc_label: Contents
toc_icon: cog
---

This document covers the Elastiknn API, including: indexing settings, REST API payloads, all aproximate similarity models, and some nice-to-know implementation details.

Once you've [installed Elastiknn](/installation/), you can use the REST API just like you would use the [official Elasticsearch REST APIs](https://www.elastic.co/guide/en/elasticsearch/reference/current/rest-apis.html).

## Index Settings

Bellow are some index settings which affect Elastiknn performance and behavior.

```json
PUT /my-index
{
  "settings": {
    "index": {
      "number_of_shards": 1,          # 1  
      "elastiknn": true               # 2
       
    }
  }
}
```

|#|Description|
|:--|:--|
|1|The number of shards in your index. Like all Elasticsearch queries, Elastiknn queries execute once per shard in parallel. This means you can generally speed up your queries by adding more shards to the index.|
|2|Setting this to `true` (default is `false`) yields a significant performance improvement for Elastiknn on Elasticsearch versions 7.7.x and beyond. The reason is a bit involved: Elastiknn stores vectors as binary doc values. Setting this to `true` tells Elastiknn to use a non-default Lucene setting to store doc values. Specifically, it uses the `Lucene87Codec` with `BEST_SPEED` instead of `BEST_COMPRESSION`. The default `BEST_COMPRESSION` setting saves space on disk but makes reading vectors significantly slower. If you really need to save space on disk or need to [freeze](https://www.elastic.co/guide/en/elasticsearch/reference/current/freeze-index-api.html) the index, then you should set this to `false`.|

## Vectors

You need to specify vectors when indexing documents and when running queries. 
In both cases you use the same JSON structure to define vectors.
Each vector also has a shorthand alternative, which can be convenient when using tools that don't support nested documents.
The examples below show how to specify vectors when indexing them.
The format for specifying vectors in queries is covered later.

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

```json
POST /my-index/_doc
{
    "my_vec": [0.1, 0.2, 0.3, ...]        # 2
}

```

|#|Description|
|:--|:--|
|1|JSON list of all floating point values in your vector. The length should match the `dims` in your mapping.|
|2|Shorthand alternative to #1.|

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

```json
POST /my-index/_doc
{
    "my_vec": [[1, 3, 5, ...], 100]      # 3
}

```

|#|Description|
|:--|:--|
|1|JSON list of the indices which are `true` in your vector.|
|2|The total number of indices in your vector. This should match the `dims` in your mapping.|
|3|Shorthand alternative to #1 and #2. A two-item list where the first item is the `true_indices` and the second is the `total_indices`.|

## Mappings

Before indexing vectors, you first define a mapping specifying a vector datatype, an indexing model, and the model's parameters. 
This determines which queries are supported for the indexed vectors.

### General Structure

The general mapping structure looks like this:

```json
PUT /my-index/_mapping
{
  "properties": {                               # 1
    "my_vec": {                                 # 2 
      "type": "elastiknn_sparse_bool_vector",   # 3
      "elastiknn": {                            # 4
        "dims": 100,                            # 5
        "model": "exact",                       # 6
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

This type is optimized for vectors where each index is either `true` or `false` and the majority of indices are `false`. 
For example, you might represent a bag-of-words encoding of a document, where each index corresponds to a word in a vocabulary and any single document contains a very small fraction of all words. 
Internally, Elastiknn saves space by only storing the true indices.

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

This type is optimized for vectors where each index is a floating point number, all of the indices are populated, and the dimensionality usually doesn't exceed ~1000. 
For example, you might store a word embedding or an image vector. 
Internally, Elastiknn uses Java Floats to store the values.

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

The exact model will allow you to run exact searches. 
These don't leverage any indexing constructs and have `O(n^2)` runtime, where `n` is the total number of documents.

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

### Jaccard LSH Mapping

Uses the [Minhash algorithm](https://en.wikipedia.org/wiki/MinHash) to hash and store sparse bool vectors such that they
support approximate Jaccard similarity queries.

The implementation is influenced by Chapter 3 of [Mining Massive Datasets.](http://www.mmds.org/), 
the [Spark MinHash implementation](https://spark.apache.org/docs/2.2.3/ml-features.html#minhash-for-jaccard-distance), 
the [tdebatty/java-LSH Github project](https://github.com/tdebatty/java-LSH), 
and the [Minhash for Dummies](http://matthewcasperson.blogspot.com/2013/11/minhash-for-dummies.html) blog post.

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
                "L": 99,                            # 5
                "k": 1                              # 6
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
|5|Number of hash tables. Generally, increasing this value increases recall.|
|6|Number of hash functions combined to form a single hash value. Generally, increasing this value increases precision.|

### Hamming LSH Mapping

Uses the [Bit-Sampling algorithm](http://mlwiki.org/index.php/Bit_Sampling_LSH) to hash and store sparse bool vectors
such that they support approximate Hamming similarity queries.

Only difference from the canonical bit-sampling method is that it samples and combines `k` bits to form a single hash value.
For example, if you set `L = 100, k = 3`, it samples `100 * 3 = 300` bits from the vector and concatenates sets of 3 
bits to form each hash value, for a total of 100 hash values.

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
                "L": 99,                            # 5
                "k": 2
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
|5|Number of hash tables. Generally, increasing this value increases recall.|
|6|Number of hash functions combined to form a single hash value. Generally, increasing this value increases precision.|

### Cosine LSH Mapping

Uses the [Random Projection algorithm](https://en.wikipedia.org/wiki/Locality-sensitive_hashing#Random_projection)
to hash and store dense float vectors such that they support approximate Cosine similarity queries.[^note-angular-cosine]

The implementation is influenced by Chapter 3 of [Mining Massive Datasets.](http://www.mmds.org/)

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_dense_float_vector", # 1
            "elastiknn": {
                "dims": 100,                        # 2
                "model": "lsh",                     # 3
                "similarity": "cosine",             # 4
                "L": 99,                            # 5
                "k": 1                              # 6
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
|5|Number of hash tables. Generally, increasing this value increases recall.|
|6|Number of hash functions combined to form a single hash value. Generally, increasing this value increases precision.|

### L2 LSH Mapping

Uses the [Stable Distributions method](https://en.wikipedia.org/wiki/Locality-sensitive_hashing#Stable_distributions)
to hash and store dense float vectors such that they support approximate L2 (Euclidean) similarity queries.

The implementation is influenced by Chapter 3 of [Mining Massive Datasets.](http://www.mmds.org/)

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_dense_float_vector", # 1
            "elastiknn": {
                "dims": 100,                        # 2
                "model": "lsh",                     # 3
                "similarity": "l2",                 # 4
                "L": 99,                            # 5
                "k": 1,                             # 6
                "w": 3                              # 7
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
|5|Number of hash tables. Generally, increasing this value increases recall.|
|6|Number of hash functions combined to form a single hash value. Generally, increasing this value increases precision.|
|7|Integer bucket width. This determines how close two vectors have to be, when projected onto a third common vector, in order for the two vectors to share a hash value. Typical values are low single-digit integers.|

### Permutation LSH Mapping

Uses the model described in [Large-Scale Image Retrieval with Elasticsearch by Amato, et. al.](https://dl.acm.org/doi/10.1145/3209978.3210089).

This model describes a vector by the `k` indices (_positions in the vector_) with the greatest absolute values.
The intuition is that each index corresponds to some latent concept, and indices with high absolute values carry more 
information about their respective concepts than those with low absolute values.
The research for this method has focused mainly on Cosine similarity,[^note-angular-cosine] though the implementation also supports L1 and L2.

**An example**

The vector `[10, -2, 0, 99, 0.1, -8, 42, -13, 6, 0.1]` with `k = 4` is represented by indices `[4, 7, -8, 1]`.
Indices are 1-indexed and indices for negative values are negated (hence the -8). 
Indices can optionally be repeated based on their ranking.
In this example, the indices would be repeated `[4, 4, 4, 4, 7, 7, 7, -8, -8, 1]`.
Index 4 has the highest absolute value, so it's repeated `k - 0 = 4` times. 
Index 7 has the second highest absolute value, so it's repeated `k - 1 = 3` times, and so on.
The search algorithm computes the score as the size of the intersection of the stored vector's representation and the 
query vector's representation.
So for a query vector represented by `[2, 2, 2, 2, 7, 7, 7, 4, 4, 5]`, the intersection is `[7, 7, 7, 4, 4]`, producing
a score of 5. 
In some experiments, repetition has actually decreased recall, so it's advised that you try with and without repetition.

```json
PUT /my-index/_mapping
{
    "properties": {
        "my_vec": {
            "type": "elastiknn_dense_float_vector", # 1
            "elastiknn": {
                "dims": 100,                        # 2
                "model": "permutation_lsh",         # 3
                "k": 10,                            # 4
                "repeating": true                   # 5
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
|4|The number of top indices to pick.|
|5|Whether to repeat the indices proportionally to their rank. See the notes on repeating above.|


## Nearest Neighbor Queries

Elastiknn provides a query called `elastiknn_nearest_neighbors`, which can be used in a `GET /_search` request just like 
standard Elasticsearch queries, as well as in combination with standard Elasticsearch queries. 

### General Structure

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
            "similarity": "cosine",             # 5
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

### Compatibility of Vector Types and Similarities

Jaccard and Hamming similarity only work with sparse bool vectors. 
Cosine,[^note-angular-cosine] L1, and L2 similarity only work with dense float vectors. 
The following documentation assume this restriction is known.

These restrictions aren't inherent to the types and algorithms, i.e., you could in theory run cosine similarity on sparse vectors.
The restriction merely reflects the most common patterns and simplifies the implementation.

### Similarity Scoring

Elasticsearch queries must return a non-negative floating-point score. 
For Elastiknn, the score for an indexed vector represents its similarity to the query vector. 
However, not all similarity functions increase as similarity increases. 
For example, a perfect similarity for the L1 and L2 functions is 0. 
Such functions really represent _distance_ without a well-defined mapping from distance to similarity. 
In these cases Elastiknn applies a transformation to invert the score such that more similar vectors have higher scores. 
The exact transformations are described below.

|Similarity|Transformation to Elasticsearch Score|Min Value|Max Value|
|:--|:--|:--|
|Jaccard|N/A|0|1.0|
|Hamming|N/A|0|1.0|
|Cosine[^note-angular-cosine]|`cosine similarity + 1`|0|2|
|L1|`1 / (1 + l1 distance)`|0|1|
|L2|`1 / (1 + l2 distance)`|0|1|

If you're using the `elastiknn_nearest_neighbors` query with other queries, and the score values are inconvenient (e.g. huge values like 1e6), consider wrapping the query in a [Script Score Query](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html), where you can access and transform the `_score` value.

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
            "similarity": "(cosine | l1 | l2)",    # 3
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Query vector. Must match the datatype of `my_vec` or be a pointer to an indexed vector that matches the type.|
|2|Model name.|
|3|Similarity function. Must be compatible with the vector type.|

### LSH Search Strategy

All LSH search models follow roughly the same strategy. 
They first retrieve approximate neighbors based on common hash terms and then compute the exact similarity for a subset of the best approximate candidates. 
The exact steps are as follows:

1. Hash the query vector using model parameters that were specified in the indexed vector's mapping.
2. Use the hash values to construct and execute a query that finds other vectors with the same hash values.
   The query is a modification of Lucene's [TermInSetQuery](https://lucene.apache.org/core/8_5_0/core/org/apache/lucene/search/TermInSetQuery.html).
3. Take the top vectors with the most matching hashes and compute their exact similarity to the query vector.
   The `candidates` parameter controls the number of exact similarity computations.
   Specifically, we compute exact similarity for the top _`candidates`_ candidate vectors in each segment.
   As a reminder, each Elasticsearch index has >= 1 shards, and each shard has >= 1 segments.
   That means if you set `"candiates": 200` for an index with 2 shards, each with 3 segments, then you'll compute the 
   exact similarity for `2 * 3 * 200 = 1200` vectors.
   `candidates` must be set to a number greater or equal to the number of Elasticsearch results you want to get.
   Higher values generally mean higher recall and higher latency.

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
            "candidates": 50                       # 5
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
|5|Number of candidates per segment. See the section on LSH Search Strategy.|

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
            "candidates": 50                       # 5
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
|5|Number of candidates per segment. See the section on LSH Search Strategy.|
|6|Set to true to use the more-like-this heuristic to pick a subset of hashes. Generally faster but still experimental.|

### Cosine LSH Query

Retrieve dense float vectors based on approximate Cosine similarity.[^note-angular-cosine]

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {
            "field": "my_vec",                     # 1
            "vec": {                               # 2
                "values": [0.1, 0.2, 0.3, ...]
            },
            "model": "lsh",                        # 3
            "similarity": "cosine",                # 4
            "candidates": 50                       # 5
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Indexed field. Must use `lsh` mapping model with `cosine` similarity.|
|2|Query vector. Must be literal dense float or a pointer to an indexed dense float vector.|
|3|Model name.|
|4|Similarity function.|
|5|Number of candidates per segment. See the section on LSH Search Strategy.|
|6|Set to true to use the more-like-this heuristic to pick a subset of hashes. Generally faster but still experimental.|

### L1 LSH Query

Not yet implemented.

### L2 LSH Query

Retrieve dense float vectors based on approximate L2 similarity.

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {
            "field": "my_vec",                     # 1
            "vec": {                               # 2
                "values": [0.1, 0.2, 0.3, ...]
            },
            "model": "lsh",                        # 3
            "similarity": "l2",                    # 4
            "candidates": 50,                      # 5
            "probes": 2                            # 6
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
|5|Number of candidates per segment. See the section on LSH Search Strategy.|
|6|Number of probes for using the multiprobe search technique. Default value is zero. Max value is `3^k`. Generally, increasing `probes` will increase recall, will allow you to use a smaller value for `L` with comparable recall, but introduces some additional computation at query time. See the L2 LSH mapping section for more on `L` and `k`.|

### Permutation LSH Query

Retrieve dense float vectors based on the permutation LSH algorithm.
See the permutation LSH mapping for more about the algorithm.

```json
GET /my-index/_search
{
    "query": {
        "elastiknn_nearest_neighbors": {
            "field": "my_vec",                     # 1
            "vec": {                               # 2
                "values": [0.1, 0.2, 0.3, ...]
            },
            "model": "permutation_lsh",            # 3
            "similarity": "cosine",                # 4
            "candidates": 50                       # 5
        }
    }
}
```

|#|Description|
|:--|:--|
|1|Indexed field. Must use `permutation_lsh` mapping to use this query.|
|2|Query vector. Must be literal dense float or a pointer to an indexed dense float vector.|
|3|Model name.|
|4|Similarity function. Supports Cosine,[^note-angular-cosine] L1, and L2.|
|5|Number of candidates per segment. See the section on LSH Search Strategy.|

### Model and Query Compatibility

Some models can support more than one type of query. 
For example, sparse bool vectors indexed with the Jaccard LSH model support exact searches using both Jaccard and Hamming similarity. 
The opposite is _not_ true: vectors stored using the exact model do not support Jaccard LSH queries.

The tables below shows valid model/query combinations. 
Rows are models and columns are queries. 
The similarity functions are abbreviated (J: Jaccard, H: Hamming, C: Cosine,[^note-angular-cosine] L1, L2).

#### elastiknn_sparse_bool_vector

|Model / Query                  |Exact    |Sparse Indexed |Jaccard LSH |Hamming LSH |
|:--                            |:--      |:--            |:--         |:--         |
|Exact (i.e. no model specified)|✔ (J, H) |x              |x           |x           |
|Sparse Indexed                 |✔ (J, H) |✔ (J, H)       |x           |x           |
|Jaccard LSH                    |✔ (J, H) |x              |✔           |x           |
|Hamming LSH                    |✔ (J, H) |x              |x           |✔           |

#### elastiknn_dense_float_vector

|Model / Query                   |Exact         |Cosine LSH |L2 LSH |Permutation LSH|
|:--                             |:--           |:--         |:--    |:--            |
|Exact (i.e. no model specified) |✔ (C, L1, L2) |x           |x      |x              | 
|Cosine LSH                      |✔ (C, L1, L2) |✔           |x      |x              |
|L2 LSH                          |✔ (C, L1, L2) |x           |✔      |x              |
|Permutation LSH                 |✔ (C, L1, L2) |x           |x      |✔              |

## Common Patterns

### Running Nearest Neighbors Query on a Filtered Subset of Documents

It's common to filter for a subset of documents based on some property and _then_ run the `elastiknn_nearest_neighbors` query on that subset.
For example, if your docs contain a `color` keyword, you might want to find all of the docs with `"color": "blue"`, and only run `elastiknn_nearest_neighbors` on that subset.
To do this, you can use the `elastiknn_nearest_neighbors` query in a [function score query](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html)
or in a [query rescorer](https://www.elastic.co/guide/en/elasticsearch/reference/7.x/filter-search-results.html#query-rescorer).
The function score query is usually simpler, but both are covered below.

#### Using a Function Score Query

```json
GET /my-index/_search

{
  "size": 10,
  "query": {
    "function_score": {
      "query": {
        "term": { "color": "blue" }                  # 1
      },
      "functions": [                                 # 2
        {
          "elastiknn_nearest_neighbors": {           # 3
            "field": "vec",
            "similarity": "cosine",
            "model": "exact",
            "vec": {
              "values": [0.1, 0.2, 0.3, ...]
            }
          }
        }
      ]
    }
  }
}
```

|#|Description|
|:--|:--|
|1|Term query will limit the functions to only run on documents matching `"color": "blue"`.|
|2|List of functions which are applied to the matching documents.|
|3|`elastiknn_nearest_neighbors` query that is evaluated on matching documents. The query produces the similarity score, which, by default, is multiplied by the term query score. If you'd like to change this behavior, see the `score_mode`, `boost_mode`, and `weight` parameters in the Elasticsearch [function score docs](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html).|

**Caveats**

This does not yet support passing indexed vectors.

When using `"model": "lsh"`, the `"candidates"` parameter is ignored and vectors are not re-scored with the exact 
similarity like they are with a `elastiknn_nearest_neighbors` query.
Instead, the score is: `max similarity score * proportion of matching hashes`.
This is a necessary consequence of the fact that score functions take a doc ID and must immediately return a score.

#### Using a Query Rescorer 

```json
GET /my-index/_search
{
  "query": {
    "term": { "color": "blue" }                     # 1
  },
  "rescore": {
    "window_size": 10,                              # 2
    "query": {
      "rescore_query": {
        "elastiknn_nearest_neighbors": {            # 3
            "field" : "vec",
            "similarity" : "l2",
            "model" : "exact",            
            "vec" : {
                "values" : [0.1, 0.2, 0.3, ...]
            }
        }
      },
      "query_weight": 0,                            # 4
      "rescore_query_weight": 1                     # 5
    }
  }
}
```

|#|Description|
|:--|:--|
|1|Term query will limit to only run on documents containing `"color": "blue"`.|
|2|The window size controls how many of the docs that matched the term query will be considered for the nearest neighbors query.|
|3|`elastiknn_nearest_neighbors` query that evaluates L2 similarity for the "vec" field in any document containing `"color": "blue"`.|
|4|Ignore the score from the term query.|
|5|Use the score from the rescore query.|

**Caveats**

This does not yet support passing indexed vectors.

Elasticsearch has a configurable limit for the number of docs that are matched and passed to the `rescore` query.
The default is 10,000. 
You can modify the `index.max_rescore_window` setting to get around this.

Given the default limit of 10k vectors passed to the nearest neighbors query, you can typically use exact queries.
As a point of reference, exact queries on the Fashion-MNIST dataset (60k 784-dimensional vectors) run in about 250ms.

If you determine you need an approximate query for re-scoring, you should ensure that `candidates = window_size > size`.
Ideally `candidates` is 10x-100x larger than `size`.
Also, consider that it's possible for the approximate query to match fewer than `candidates` vectors.
So you can end up with fewer than `size` results in your search response.
This can happen because the nearest neighbors query is only given access to the `window_size` vectors which matched the original query.
If you run into this, you should probably adjust your approximate model parameters for higher recall.
There are notes on each model about how the parameters affect recall and precision.

### Using Stored Fields for Faster Queries

This is a fairly well-known Elasticsearch optimization that applies nicely to some elastiknn use cases.
If you only need to retrieve a small subset of the document source (e.g. only the ID), you can store the relavant fields as `stored` fields to get a meaningful speedup.
The Elastiknn scala client uses this optimization to store and retrieve document IDs, yielding a ~40% speedup for queries.
The setting [is documented here](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-store.html)
and discussed in detail [in this Github issue.](https://github.com/elastic/elasticsearch/issues/17159)

## Nice to Know

Here are some other things worth knowing. 
Perhaps there will be a more cohesive way to present these in the future.

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

### Parallelism

From Elasticsearch's perspective, the `elastiknn_nearest_neighbors` query is no different than any other query. 
Elasticsearch receives a JSON query containing an `elastiknn_nearest_neighbors` key, passes the JSON to a parser implemented by Elastiknn, the parser produces a Lucene query, and Elasticsearch executes that query on each shard in the index. 
This means the simplest way to increase query parallelism is to add shards to your index. 
Obviously this has an upper limit, but the general performance implications of sharding are beyond the scope of this document.

---

[^note-angular-cosine]: Cosine similarity used to be (incorrectly) called "angular" similarity. All references to "angular" were renamed to "Cosine" in 7.13.3.2. You can still use "angular" in the JSON/HTTP API; it will convert to "cosine" internally. 