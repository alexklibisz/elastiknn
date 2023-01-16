# Elastiknn

Elasticsearch plugin for similarity search on dense floating point and sparse boolean vectors.

## Documentation

**[Comprehensive documentation is hosted at elastiknn.com](https://elastiknn.com)**

## Support

- For questions, ideas, feature requests, and other discussion, start a [Github discussion](https://github.com/alexklibisz/elastiknn/discussions).
- For obvious bugs, post a [Github issue](https://github.com/alexklibisz/elastiknn/issues).

## Contributing

To contribute to Elastiknn, please see developer-guide.md.

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
## Sponsors

[![Yourkit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/)

YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/), and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

<!-- Links -->

[Link-Github-CI]: https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ACI
[Badge-Github-CI]: https://img.shields.io/github/actions/workflow/status/alexklibisz/elastiknn/ci.yml?branch=main&style=for-the-badge "Github CI Workflow"

[Link-Github-Release]: https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ARelease
[Badge-Github-Release]: https://img.shields.io/github/actions/workflow/status/alexklibisz/elastiknn/release.yml?branch=main&style=for-the-badge "Github Release Workflow"

[Link-Plugin-Release]: https://github.com/alexklibisz/elastiknn/releases/latest
[Badge-Plugin-Release]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?style=flat-square "Plugin Release"
[Link-Plugin-Snapshot]: https://github.com/alexklibisz/elastiknn/releases
[Badge-Plugin-Snapshot]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?include_prereleases&style=flat-square "Plugin Snapshot"
[Badge-Plugin-Downloads]: https://img.shields.io/github/downloads/alexklibisz/elastiknn/total?style=flat-square

[Link-Python-Release]: https://pypi.org/project/elastiknn-client/
[Badge-Python-Release]: https://img.shields.io/pypi/v/elastiknn-client?style=flat-square "Python Release"
[Badge-Python-Downloads]: https://img.shields.io/pypi/dm/elastiknn-client?style=flat-square
