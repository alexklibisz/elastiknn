---
layout: default
title: Concepts
nav_order: 3
description: "Describing Elastiknn Concepts"
permalink: /concepts/
---

# Concepts
{: .no_toc }

1. TOC
{:toc}

## Overview

Like most nearest neighbors search systems, Elastiknn's functionality can be broken into indexing and search.
This document will discuss the data structures and algorithms used for indexing and search. 
If you want a precise enumeration of the API, see the [JSON API page](/json-api/).

## Indexing

## Search

## Mappings, Datatypes, Models

In order to store vectors you first define a [mapping](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html) where one of the fiels uses an Elastiknn vector datatype.

Alongside the datatype, you specify the vector's dimensions and an optional model. The model determines how each vector is pre-processed to support different kinds of searches. It's not required to support exact (i.e. exhaustive) queries, but is required if you want to run approximate queries.

A mapping is applied using a PUT request, for example:

```sh
curl -X PUT "localhost:9200/my-index/_mapping?pretty" -H 'Content-Type: application/json' -d'
{
  "properties": {
    "my_vec": {
      "type": "elastiknn_sparse_bool_vector",
      "elastiknn": {
        "dims": 9999,
        "model": "sparse_indexed"
      }
    }
  }
}'
```

### Datatypes

#### elastiknn_dense_float_vector datatype

The `elastiknn_dense_float_vector` is optimized for vectors of floating point numbers where all of the indices hold a value. Typically these contain anywhere up to about 1000 values. They can hold more, but you're probably better off performing some dimensionality reduction to get down to 1000 or fewer. Internally they are stored as 32-bit Java Floats.

#### elastiknn_sparse_bool_vector datatype

The `elastiknn_sparse_bool_vector` is optimized for sparse vectors in which each index is either `true` or `false` (i.e. present/missing) and a small fraction of the indices in any given vector are `true`. 
They are stored in a way that only enumerates the `true` indices to avoid wasting space.

### Models

TODO

## Vectors

TODO

## Queries

TODO
