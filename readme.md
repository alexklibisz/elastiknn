# ElastiKnn 

An Elasticsearch plugin for exact and approximate K-nearest-neighbors search in high-dimensional vector spaces.

|Item|Status|
|:--|:--|
|[Github CI Workflow](https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ACI)|![GitHub CI Workflow](https://img.shields.io/github/workflow/status/alexklibisz/elastiknn/CI?style=for-the-badge)|
|[Github Release Workflow](https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ARelease)|![Github Release Workflow](https://img.shields.io/github/workflow/status/alexklibisz/elastiknn/Release?style=for-the-badge)|
|[Plugin Zip File, Release](https://github.com/alexklibisz/elastiknn/releases/latest)|![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/alexklibisz/elastiknn?style=for-the-badge)|
|[Plugin Zip File, Snapshot](https://github.com/alexklibisz/elastiknn/releases)|![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/v/release/alexklibisz/elastiknn?include_prereleases&style=for-the-badge)|
|[Client Library, Scala 2.12, Release](https://search.maven.org/search?q=g:com.klibisz.elastiknn)|![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/com.klibisz.elastiknn/client-elastic4s_2.12?server=https%3A%2F%2Foss.sonatype.org&style=for-the-badge)|
|[Client Library, Scala 2.12, Snapshot](https://oss.sonatype.org/#nexus-search;quick~com.klibisz.elastiknn)|![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.klibisz.elastiknn/client-elastic4s_2.12?server=https%3A%2F%2Foss.sonatype.org&style=for-the-badge)|

## Work in Progress

This project is very much a work-in-progress. I've decided to go ahead and make the repo public since
some people have expressed interest through emails and LinkedIn messages. 

The "Features" section below is a good picture of my high level goals for this project. As of late January, I've
completed an ingest processor, custom queries, a custom mapper to store `ElastiKnnVector`s, exact
similarity queries for all five similarity functions, MinHash LSH for Jaccard similarity, and setup a
fairly robust testing harness. The testing harness took a surprising amount of work.

If you want to contribute, you'll have to dig around quite a bit for now. The Makefile is a good place to start. 
Generally speaking, there are several Gradle projects: the plugin (currently on ES 7.4x); a "core" project 
containing protobuf definitions, models, and some utilities; a scala client based on the Elastic4s library;
a reference project which compares elastiknn implementations to others (e.g. spark). There's also a Python
client which provides a client roughly equivalent to the Scala one and a scikit-learn-style client.

Feel free to open issues and PRs, but I don't plan to initially spend a lot of time documenting or coordinating
contributions until the code churn decreases. I'll do my best to keep the readme updated and I've made
a Github project board to track ongoing work. 

## Features

1. Exact nearest neighbors search. This should only be used for testing and on relatively small datasets.
1. Approximate nearest neighbors search using Locality Sensitive Hashing (LSH) and Multiprobe LSH. This scales well for large datasets; see the Performance section below for details.
1. Supports dense floating point vectors and sparse boolean vectors.
1. Supports five distance functions: L1, L2, Angular, Hamming, and Jaccard.
1. Supports the two most common nearest neighbors queries: 
	- k nearest neighbors - i.e. _"give me the k nearest neighbors to some query vector"_
	- fixed-radius nearest neighbors - i.e. _"give me all neighbors within some radius of a query vector"_
1. Integrates nearest neighbor queries with existing Elasticsearch queries.
1. Horizontal scalability. Vectors are stored as regular Elasticsearch documents and queries are implemented using standard Elasticsearch constructs.

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

In no particular order:

- [Alex Reelsen](https://github.com/spinscale) has several open-source plugins which were useful examples for the general structure of a plugin project: 
- [Mining of Massive Datasets (MMDS) by Leskovec, et. al](http://www.mmds.org/), particularly chapter 3, is a great reference for approximate similarity search.
- [The Read Only Rest Plugin](https://github.com/sscarduzio/elasticsearch-readonlyrest-plugin) served as an example for much of the Gradle and testing setup.
- [The Scalable Data Science Lectures on Youtube](https://www.youtube.com/playlist?list=PLbRMhDVUMngekIHyLt8b_3jQR7C0KUCul) were helpful for better understanding LSH. I think much of that content is also based on the MMDS book.
