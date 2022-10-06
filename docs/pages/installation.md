---
layout: single
title: Installation
description: "Installing the Elastiknn Plugin"
permalink: /installation/
classes: wide
---

The Elastiknn plugin gets published as a zip file in a Github release. [The lastest release can be found here.](https://github.com/alexklibisz/elastiknn/releases/latest)

To install it, copy the zip file URL and run `elasticsearch-plugin install <THE URL>` on each of your Elasticsearch nodes. 

## Versions

|:--|:--|
|Plugin Release| [![Plugin Release Status][Badge-Plugin-Release]][Link-Plugin-Release]|
|Plugin Snapshot| [![Plugin Snapshot Status][Badge-Plugin-Snapshot]][Link-Plugin-Snapshot]|

[Link-Plugin-Release]: https://github.com/alexklibisz/elastiknn/releases/latest
[Link-Plugin-Snapshot]: https://github.com/alexklibisz/elastiknn/releases

[Badge-Plugin-Release]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?style=for-the-badge "Plugin Release"
[Badge-Plugin-Snapshot]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?include_prereleases&style=for-the-badge "Plugin Snapshot"

**A caveat on versioning:** Elasticsearch requires that the plugin's Elasticsearch version matches the version on the node. For now I'm only releasing the plugin for a single Elasticsearch version. I'd like to eventually come back and implement releases for multiple versions.

## Example Installation in a Docker Image

Make a Dockerfile like below. The image version (`elasticsearch:A.B.C`) must match the plugin's version (e.g. `A.B.C.x/elastiknn-A.B.C.x`).
`A.B.C` is the Elasticsearch version. `.x` just refers to an incremental version of Elastiknn on top of `A.B.C`.

```docker
FROM docker.elastic.co/elasticsearch/elasticsearch:8.4.3
RUN elasticsearch-plugin install --batch https://github.com/alexklibisz/elastiknn/releases/download/8.4.3.1/elastiknn-8.4.3.1.zip
```

Build and run the Dockerfile. If you have any issues please refer to the [official docs.](https://www.elastic.co/guide/en/elasticsearch/reference/current/docker.html)

```sh
$ docker build -t elastiknn-example .
$ sudo sysctl -w vm.max_map_count=262144 # Have to do this on Ubuntu host; not sure about others.
$ docker run -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" -e "xpack.security.enabled=false" elastiknn-example
```

In another terminal, use curl to check the health status and make sure the plugin is installed.

```sh
$ curl localhost:9200/_cat/health
1586481957 01:25:57 docker-cluster green 1 1 0 0 0 0 0 0 - 100.0%
$ curl localhost:9200/_cat/plugins
ccba91520728 elastiknn <version>
```


