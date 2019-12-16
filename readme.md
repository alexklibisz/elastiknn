# ElastiKnn

ElastiKnn is an Elasticsearch plugin for exact and approximate nearest neighbors search in high-dimensional vector spaces.

## Features

1. Exact nearest neighbors search. This should only be used for testing and on relatively small datasets.
2. Approximate nearest neighbors search using Locality Sensitive Hashing (LSH) and Multiprobe LSH. This scales well for large datasets; see the Performance section below for details.
3. Supports five distance functions: L1, L2, Angular, Hamming, and Jaccard.
4. Supports the two most common nearest neighbors queries: 
	- k nearest neighbors - i.e. _"give me the k nearest neighbors to some query vector"_
	- fixed-radius nearest neighbors - i.e. _"give me all neighbors within some radius of a query vector"_
4. Integrates nearest neighbor queries with existing Elasticsearch queries.
5. Horizontal scalability. Vectors are stored as regular Elasticsearch documents and queries are implemented using standard Elasticsearch constructs.

## Usage

### Install ElastiKnn on an ElasticSearch cluster

### Run a Docker container with ElastiKnn already installed

### Exact search using the Elasticsearch REST API

### Python Client

### Scala Client

## Performance

### Ann-Benchmarks

### Million-Scale

### Billion-Scale

## Development

## References