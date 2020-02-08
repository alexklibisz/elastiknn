# ElastiKnn 

An Elasticsearch plugin for exact and approximate K-nearest-neighbors search in high-dimensional vector spaces.

## Builds and Releases

|Item|Status|
|:--|:--|
|Github CI Build| [![Github CI Status][Badge-Github-CI]][Link-Github-CI]|
|Github Release Build| [![Github Release Status][Badge-Github-Release]][Link-Github-Release]|
|Plugin Release| [![Plugin Release Status][Badge-Plugin-Release]][Link-Plugin-Release]|
|Plugin Snapshot| [![Plugin Snapshot Status][Badge-Plugin-Snapshot]][Link-Plugin-Snapshot]|
|Python Client, Release| [![Python Client Release Status][Badge-Python-Release]][Link-Python-Release]|
|Scala 2.12 Client, Release| [![Scala Client Release Status][Badge-Scala-Release]][Link-Scala-Release]|
|Scala 2.12 Client, Snapshot| [![Scala Client Snapshot Status][Badge-Scala-Snapshot]][Link-Scala-Snapshot]|

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

### Builds and Releases

There are three main artifacts produced by this project:

1. The actual plugin, which is a zip file published to Github releases.
2. The python client library, which gets published to PyPi.
3. The scala client library, which gets published to Sonatype.

All three artifacts are built and published as "snapshots" on every PR commit and every push/merge to master. All three
artifacts are released on every push/merge to master in which the `version` file has changed. We detect a change in the
version file by checking if a release tag exists with the same name as the version.

All of this is handled by Github Workflows with all steps defined in the yaml files in `.github/workflows`.

## References

In no particular order:

- [Alex Reelsen](https://github.com/spinscale) has several open-source plugins which were useful examples for the general structure of a plugin project: 
- [Mining of Massive Datasets (MMDS) by Leskovec, et. al](http://www.mmds.org/), particularly chapter 3, is a great reference for approximate similarity search.
- [The Read Only Rest Plugin](https://github.com/sscarduzio/elasticsearch-readonlyrest-plugin) served as an example for much of the Gradle and testing setup.
- [The Scalable Data Science Lectures on Youtube](https://www.youtube.com/playlist?list=PLbRMhDVUMngekIHyLt8b_3jQR7C0KUCul) were helpful for better understanding LSH. I think much of that content is also based on the MMDS book.

<!-- Links -->

[Link-Github-CI]: https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ACI
[Link-Github-Release]: https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ARelease
[Link-Plugin-Release]: https://github.com/alexklibisz/elastiknn/releases/latest
[Link-Plugin-Snapshot]: https://github.com/alexklibisz/elastiknn/releases
[Link-Python-Release]: https://pypi.org/project/elastiknn-client/
[Link-Scala-Release]: https://search.maven.org/search?q=g:com.klibisz.elastiknn
[Link-Scala-Snapshot]: https://oss.sonatype.org/#nexus-search;quick~com.klibisz.elastiknn

[Badge-Github-CI]: https://img.shields.io/github/workflow/status/alexklibisz/elastiknn/CI?style=for-the-badge "Github CI Workflow"
[Badge-Github-Release]: https://img.shields.io/github/workflow/status/alexklibisz/elastiknn/Release?style=for-the-badge "Github Release Workflow"
[Badge-Plugin-Release]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?style=for-the-badge "Plugin Release"
[Badge-Plugin-Snapshot]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?include_prereleases&style=for-the-badge "Plugin Snapshot"
[Badge-Python-Release]: https://img.shields.io/pypi/v/elastiknn-client?style=for-the-badge "Python Release"
[Badge-Scala-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/client-elastic4s_2.12?server=https%3A%2F%2Foss.sonatype.org&style=for-the-badge
[Badge-Scala-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/client-elastic4s_2.12?server=https%3A%2F%2Foss.sonatype.org&style=for-the-badge
