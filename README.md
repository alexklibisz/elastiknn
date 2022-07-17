# Elastiknn

Elasticsearch plugin for similarity search on dense floating point and sparse boolean vectors.

## Documentation

**[Comprehensive documentation is hosted at elastiknn.com](https://elastiknn.com)**

If you're looking to contribute to Elastiknn, please see developer-guide.md.

## Support

- For questions, ides, feature requests, and other discussion, start a [Github discussion](https://github.com/alexklibisz/elastiknn/discussions).
- For obvious bugs and feature requests, post a [Github issue](https://github.com/alexklibisz/elastiknn/issues).

## Users

Are you using Elastiknn? If so, please consider submitting a pull request to list your organization below.

- [Apache Jackrabbit](https://jackrabbit.apache.org): Uses Elastiknn for image similarity search
- [rep0st](https://github.com/ReneHollander/rep0st): Uses Elastiknn for reverse image lookups in a set of millions of images
- [Orlo](https://orlo.tech): Uses Elastiknn for indexing deep text understanding in text to provide interesting insights

## Builds

|Build|Status|
|:--|:--|
|Github CI Builds       |[![Github CI Status][Badge-Github-CI]][Link-Github-CI]               |
|Github Release Build   |[![Github Release Status][Badge-Github-Release]][Link-Github-Release]|

## Releases

|Artifact|Release|Snapshot|Downloads|
|:--|:--|:--|:--|
|Elasticsearch plugin zip file                                                                | [![Plugin Release][Badge-Plugin-Release]][Link-Plugin-Release]          | [![Plugin Snapshot][Badge-Plugin-Snapshot]][Link-Plugin-Snapshot]            | ![Badge-Plugin-Downloads] |
|Python HTTP client for Elastiknn                                                             | [![Python Release][Badge-Python-Release]][Link-Python-Release]          |                                                                              | ![Badge-Python-Downloads] |
|Java library w/ exact and approximate vector similarity models                               | [![Models Release][Badge-Models-Release]][Link-Models-Release]          | [![Models Snapshot][Badge-Models-Snapshot]][Link-Models-Snapshot]            |                           |
|Java library w/ Lucene queries and constructs used in Elastiknn                              | [![Lucene Release][Badge-Lucene-Release]][Link-Lucene-Release]          | [![Lucene Snapshot][Badge-Lucene-Snapshot]][Link-Lucene-Snapshot]            |                           |
|Java client w/ Elasticsearch query builder                              | [![Client Java Release][Badge-Client-Java-Release]][Link-Client-Java-Release]          | [![Client Java Snapshot][Badge-Client-Java-Snapshot]][Link-Client-Java-Snapshot]            |                           |
|Scala case classes and circe codecs for the Elastiknn JSON API                               | [![Api4s Release][Badge-Api4s-Release]][Link-Api4s-Release]        	| [![Api4s Snapshot][Badge-Api4s-Snapshot]][Link-Api4s-Snapshot]               |                           |
|Scala HTTP client for Elastiknn, based on [elastic4s](https://github.com/sksamuel/elastic4s) | [![Elastic4s Release][Badge-Elastic4s-Release]][Link-Elastic4s-Release] | [![Elastic4s Snapshot][Badge-Elastic4s-Snapshot]][Link-Elastic4s-Snapshot]   |                           |

## Sponsors

[![Yourkit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/)

YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/), and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

<!-- Links -->

[Link-Github-CI]: https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ACI
[Badge-Github-CI]: https://img.shields.io/github/workflow/status/alexklibisz/elastiknn/CI?style=for-the-badge "Github CI Workflow"

[Link-Github-Release]: https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ARelease
[Badge-Github-Release]: https://img.shields.io/github/workflow/status/alexklibisz/elastiknn/Release?style=for-the-badge "Github Release Workflow"

[Link-Plugin-Release]: https://github.com/alexklibisz/elastiknn/releases/latest
[Badge-Plugin-Release]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?style=flat-square "Plugin Release"
[Link-Plugin-Snapshot]: https://github.com/alexklibisz/elastiknn/releases
[Badge-Plugin-Snapshot]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?include_prereleases&style=flat-square "Plugin Snapshot"
[Badge-Plugin-Downloads]: https://img.shields.io/github/downloads/alexklibisz/elastiknn/total?style=flat-square

[Link-Python-Release]: https://pypi.org/project/elastiknn-client/
[Badge-Python-Release]: https://img.shields.io/pypi/v/elastiknn-client?style=flat-square "Python Release"
[Badge-Python-Downloads]: https://img.shields.io/pypi/dm/elastiknn-client?style=flat-square

[Badge-Models-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/models?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "models release"
[Badge-Models-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/models?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "models snapshot"
[Link-Models-Release]: https://search.maven.org/artifact/com.klibisz.elastiknn/models
[Link-Models-Snapshot]: https://oss.sonatype.org/#nexus-search;gav~com.klibisz.elastiknn~models~~~

[Badge-Lucene-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/lucene?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "lucene release"
[Badge-Lucene-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/lucene?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "lucene snapshot"
[Link-Lucene-Release]: https://search.maven.org/artifact/com.klibisz.elastiknn/lucene
[Link-Lucene-Snapshot]: https://oss.sonatype.org/#nexus-search;gav~com.klibisz.elastiknn~lucene~~~

[Badge-Client-Java-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/client-java?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "client java release"
[Badge-Client-Java-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/client-java?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "client java snapshot"
[Link-Client-Java-Release]: https://search.maven.org/artifact/com.klibisz.elastiknn/client-java
[Link-Client-Java-Snapshot]: https://oss.sonatype.org/#nexus-search;gav~com.klibisz.elastiknn~client-java~~~

[Badge-Api4s-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/api4s_2.12?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "api4s_2.12 release"
[Badge-Api4s-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/api4s_2.12?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "api4s_2.12 snapshot"
[Link-Api4s-Release]: https://search.maven.org/artifact/com.klibisz.elastiknn/api4s_2.12
[Link-Api4s-Snapshot]: https://oss.sonatype.org/#nexus-search;gav~com.klibisz.elastiknn~api4s_2.12~~~

[Badge-Elastic4s-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/client-elastic4s_2.12?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "client-elastic4s_2.12 release"
[Badge-Elastic4s-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/client-elastic4s_2.12?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "client-elastic4s_2.12 snapshot"
[Link-Elastic4s-Release]: https://search.maven.org/artifact/com.klibisz.elastiknn/client-elastic4s_2.12
[Link-Elastic4s-Snapshot]: https://oss.sonatype.org/#nexus-search;gav~com.klibisz.elastiknn~client-elastic4s_2.12~~~