# ElastiKnn

ElastiKnn is an Elasticsearch plugin for exact and approximate nearest neighbors search in high-dimensional vector spaces.

## Work in Progress

This project is very much a work-in-progress. I've decided to go ahead and make the repo public since
some people have expressed interest through emails and LinkedIn messages. If you want to contribute,
you'll have to dig around quite a bit for now. The Makefile is a good place to start. I'll do my best
to keep the readme updated and am considering making a Github project board to track my ongoing work.

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

TODO

### Run a Docker container with ElastiKnn already installed

TODO

### Exact search using the Elasticsearch REST API

TODO

### Python Client

TODO

### Scala Client

TODO

## Performance

### Ann-Benchmarks

Currently working on this in a fork of the [Ann-Benchmarks repo here](https://github.com/alexklibisz/ann-benchmarks).
Planning to submit a PR when all of the approximate similarities are implemented and the Docker image can be built with
a release elastiknn zip file. 

### Million-Scale

TODO

Planning to implement this using one of the various word vector datasets.

### Billion-Scale

TODO

Not super sure of the feasability of this yet. There are some notes in benchmarks/billion.

## Development

## References