---
layout: default
title: Scala Client
nav_order: 3
description: "Documentation for the Elastiknn Scala Client"
permalink: /scala-client
---

# Scala Client

The Scala client provides a type-safe, asynchronous interface for using the Elastiknn plugin from your Scala projects.
It's built on top of the popular [elastic4s library](https://github.com/sksamuel/elastic4s) and abstracts over effect types.
A default instance for Scala `Future`s ships with the client.

## Documentation

<a href="/docs/scaladoc/com/klibisz/elastiknn/client/" target="_blank">Scaladocs are hosted here.</a> These cover the client and related code from the `core` project.

## Versions

|:--|:--|
|Scala 2.12, Release| [![Scala Client Release Status][Badge-Scala-Release]][Link-Scala-Release]|
|Scala 2.12, Snapshot| [![Scala Client Snapshot Status][Badge-Scala-Snapshot]][Link-Scala-Snapshot]|

[Link-Scala-Release]: https://search.maven.org/search?q=g:com.klibisz.elastiknn
[Link-Scala-Snapshot]: https://oss.sonatype.org/#nexus-search;quick~com.klibisz.elastiknn

[Badge-Scala-Release]: https://img.shields.io/nexus/r/com.klibisz.elastiknn/client-elastic4s_2.12?server=https%3A%2F%2Foss.sonatype.org&style=flat-square
[Badge-Scala-Snapshot]: https://img.shields.io/nexus/s/com.klibisz.elastiknn/client-elastic4s_2.12?server=https%3A%2F%2Foss.sonatype.org&style=flat-square

## Installing in your project

For sbt, simply add the following dependency:

```scala
libraryDependencies += "com.klibisz.elastiknn" %% "client-elastic4s" % <version above>
```

For other build tools, please see the instructions alongside each artifact on the Sonatype repository (click the badges in the Versions table above).





