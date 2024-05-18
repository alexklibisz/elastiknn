---
layout: single
title: Installation
description: "Installing the Elastiknn Plugin"
permalink: /installation/
classes: wide
---

## Overview

To install Elastiknn:

1. Copy the zip file URL from an Elastiknn [Github release](https://github.com/alexklibisz/elastiknn/releases).
2. Run `elasticsearch-plugin install <zip file URL>` on each Elasticsearch node. 

## Versions

### Versioning Scheme

Elasticsearch requires a new version of Elastiknn for every version of Elasticsearch.
Because of this, Elastiknn's versioning scheme includes the Elasticsearch version, followed by an incrementing version denoting changes to Elastiknn.
For example, Elastiknn versions `8.4.2.0` and `8.4.2.1` were the first and second releases of Elastiknn corresponding to Elasticsearch version 8.4.2.  

Elastiknn's main branch and the documentation site stay up-to-date with the latest Elasticsearch version, currently 8.x.
We maintain a second branch, [elasticsearch-7x](https://github.com/alexklibisz/elastiknn/tree/elasticsearch-7x), for Elasticsearch 7.x releases.

### Elasticsearch 8.x

|:--|:--|
|Plugin Release| [![Plugin Release Status][Badge-Plugin-Release]][Link-Plugin-Release]|

[Link-Plugin-Release]: https://github.com/alexklibisz/elastiknn/releases/latest
[Badge-Plugin-Release]: https://img.shields.io/github/v/release/alexklibisz/elastiknn?style=for-the-badge "Plugin Release"

### Elasticsearch 7.x

To find the latest 7.x release, please browse the [Github releases](https://github.com/alexklibisz/elastiknn/releases).

## Example Installation in a Docker Image

Make a Dockerfile like below. 
The image version (`elasticsearch:A.B.C`) must match the plugin's version (e.g. `A.B.C.x/elastiknn-A.B.C.x`).

```docker
FROM docker.elastic.co/elasticsearch/elasticsearch:8.13.4
RUN elasticsearch-plugin install --batch https://github.com/alexklibisz/elastiknn/releases/download/8.13.4.0/elastiknn-8.13.4.0.zip
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


