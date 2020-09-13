---
layout: default
title: Libraries
nav_order: 3
description: "Documentation for the Elastiknn Libraries"
permalink: /libraries/
---

# Libraries
{: .no_toc }

The Elastiknn project includes some additional Python, Java, and Scala libraries.

1. TOC
{: toc}

## Python client


The Python client provides a pythonic interface for using Elastiknn.
This includes a low level client that roughly mirrors the [Scala client](/scala-client), as well as a higher level "model" that mimics the scikit-learn interface.

**Install**

`pip install elastiknn-client`

**Documentation**

- **<a href="/docs/pdoc" target="_blank">PDoc for latest the release</a>**
- <a href="http://archive.elastiknn.klibisz.com" target="_blank">Archived docs from previous releases</a>

**Versions**

|:--|:--|
|Release|[![Python Release][Badge-Python-Release]][Link-Python-Release]|

## Java library with exact and approximate similarity models

This library contains the exact and approximate similarity models used by Elastiknn.

**Install**

In a Gradle project:

```groovy
implementation 'com.klibisz.elastiknn:models:<version below>'
```

**Versions**

|:--|:--|
|Release|[![Models Release][Badge-Models-Release]][Link-Models-Release]|
|Snapshot|[![Models Snapshot][Badge-Models-Snapshot]][Link-Models-Snapshot]|


## Java library with custom Lucene queries

This library contains a custom Lucene query used by Elastiknn, as well as a handful of related utilities.

**Install**

In a Gradle project:

```groovy
implementation 'com.klibisz.elastiknn:lucene:0.1.0-PRE30'
```

**Versions**

|:--|:--|
|Rekease|[![Lucene Release][Badge-Lucene-Release]][Link-Lucene-Release]|
|Snapshot|[![Lucene Snapshot][Badge-Lucene-Snapshot]][Link-Lucene-Snapshot]|


## Scala client

The Scala client provides type-safe, asynchronous methods for using the Elastiknn plugin from your Scala project.
It's built on top of the popular [elastic4s library](https://github.com/sksamuel/elastic4s) and abstracts over effect types.
A default instance for Scala `Future`s ships with the client.

**Install**

In an sbt project:

```scala
libraryDependencies += "com.klibisz.elastiknn" %% "client-elastic4s" % <version below>
```

**Documentation**

- **<a href="/docs/scaladoc/com/klibisz/elastiknn/client/" target="_blank">Scaladocs for the latest release</a>**
- <a href="http://archive.elastiknn.klibisz.com" target="_blank">Archived docs from previous releases</a>

**Versions**

|:--|:--|
|Release| [![Elastic4s Release][Badge-Elastic4s-Release]][Link-Elastic4s-Release] | 
|Snapshot| [![Elastic4s Snapshot][Badge-Elastic4s-Snapshot]][Link-Elastic4s-Snapshot]|

## Scala case classes and codecs for the JSON API

This library contains case classes and [Circe](https://github.com/circe/circe) codecs for the data structures in Elastiknn's JSON API.

**Install**

In an sbt project:

```scala
libraryDependencies += "com.klibisz.elastiknn" %% "api4s" % <version below>
```

**Versions**

|:--|:--|
|Release|[![Api4s Release][Badge-Api4s-Release]][Link-Api4s-Release]|
|Snapshot|[![Api4s Snapshot][Badge-Api4s-Snapshot]][Link-Api4s-Snapshot]|


<!-- Links -->

[Link-Github-CI]: https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ACI
[Badge-Github-CI]: https://img.shields.io/github/workflow/status/alexklibisz/elastiknn/CI?style=for-the-badge "Github CI Workflow"

[Link-Github-Release]: https://github.com/alexklibisz/elastiknn/actions?query=workflow%3ARelease
[Badge-Github-Release]: https://img.shields.io/github/workflow/status/alexklibisz/elastiknn/Release?style=for-the-badge "Github Release Workflow"

[Link-Plugin-Release]: https://github.com/alexklibisz/elastiknn/releases/latest
[Badge-Plugin-Release]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?style=flat-square "Plugin Release"
[Link-Plugin-Snapshot]: https://github.com/alexklibisz/elastiknn/releases
[Badge-Plugin-Snapshot]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?include_prereleases&style=flat-square "Plugin Snapshot"

[Link-Python-Release]: https://pypi.org/project/elastiknn-client/
[Badge-Python-Release]: https://img.shields.io/pypi/v/elastiknn-client?style=flat-square "Python Release"

[Badge-Models-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/models?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "models release"
[Badge-Models-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/models?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "models snapshot"
[Link-Models-Release]: https://search.maven.org/artifact/com.klibisz.elastiknn/models
[Link-Models-Snapshot]: https://oss.sonatype.org/#nexus-search;gav~com.klibisz.elastiknn~models~~~

[Badge-Lucene-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/lucene?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "lucene release"
[Badge-Lucene-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/lucene?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "lucene snapshot"
[Link-Lucene-Release]: https://search.maven.org/artifact/com.klibisz.elastiknn/lucene
[Link-Lucene-Snapshot]: https://oss.sonatype.org/#nexus-search;gav~com.klibisz.elastiknn~lucene~~~

[Badge-Api4s-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/api4s_2.12?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "api4s_2.12 release"
[Badge-Api4s-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/api4s_2.12?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "api4s_2.12 snapshot"
[Link-Api4s-Release]: https://search.maven.org/artifact/com.klibisz.elastiknn/api4s_2.12
[Link-Api4s-Snapshot]: https://oss.sonatype.org/#nexus-search;gav~com.klibisz.elastiknn~api4s_2.12~~~

[Badge-Elastic4s-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/client-elastic4s_2.12?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "client-elastic4s_2.12 release"
[Badge-Elastic4s-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/client-elastic4s_2.12?server=http%3A%2F%2Foss.sonatype.org&style=flat-square "client-elastic4s_2.12 snapshot"
[Link-Elastic4s-Release]: https://search.maven.org/artifact/com.klibisz.elastiknn/client-elastic4s_2.12
[Link-Elastic4s-Snapshot]: https://oss.sonatype.org/#nexus-search;gav~com.klibisz.elastiknn~client-elastic4s_2.12~~~
