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

## Features

### Completed

**Exact KNN search for five distance functions: L1, L2, Angular, Hamming, and Jaccard.**

This is fairly thoroughly profiled and optimized, but it's still an n^2 algorithm so it should only be used for testing 
and on relatively small datasets.

**Approximate KNN using Locality Sensitive Hashing; currently only works for Jaccard similarity**

This should scale much better for large datasets. I'm working on implementations for the other similarity functions as
well as a multiprobe-LSH variant where possible.

**Pipeline Processors for ingesting vectors**

The plugin uses [pipeline processors](https://www.elastic.co/guide/en/elasticsearch/reference/current/pipeline-processor.html) 
to validate, pre-process, and index vectors. This means you can just pass in the vectors as JSON documents.

**Integrates KNN queries seamlessly with existing Elasticsearch queries**

The exact and approximate KNN searches are implemented as Elasticsearch queries. This means you can store your vectors
inside of existing documents and mix your queries into existing queries. The semantics resemble 
[GeoShape queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-geo-shape-query.html).
Under the hood, the queries are implemented using standard Elasticsearch constructs, specifically 
[Function Score Queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html)
and [Boolean Queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-bool-query.html).

### Forthcoming

**Approximate KNN for L1, L2, Angular and Hamming similarities**

The API will be the same as it is for Jaccard. It's just a matter of implementing and optimizing the internals.

**Multiprobe LSH Queries**

Multiprobe LSH has been shown to improve performance. I'm planning to implement this for the similarity functions where
it makes sense. The API should be a simple extension of the existing approximate queries.

**Fixed radius KNN queries**

Instead of returning the nearest vectors, this query will return the vectors that fall within some radius of a given
query vector.

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
