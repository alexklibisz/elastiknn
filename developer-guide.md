# Elastiknn Developer Guide

## Introduction

If you're reading this, there's a chance you'd like to contribute to Elastiknn. Very nice!

## Local Development Setup

### Prerequisites

You need at least the following software installed: git, make, Java 14, Python3, docker, and docker-compose.
I'm assuming you're running on a Linux or OSX operating system. I have no idea if any of this will work on Windows.
There might be other software which is missing. If so, please submit an issue or PR.

### Run a local Elasticsearch instance with the plugin installed

Once you have the prerequisites installed, clone the project and run:

```
make run/gradle
```

This starts a local instance of Elasticsearch with the plugin installed. 
It can take about five minutes the first time you run it. 

Once you see "EXECUTING", you should open another shell and run `curl localhost:9200`.
You should see the usual Elasticsearch JSON response containing the version, cluster name, etc.

### Project Structure

Elastiknn currently consists of several subprojects managed by Make and Gradle:

- client-python - Python client.
- elastiknn-api4s - Gradle project containing Scala case classes that model the Elastiknn API.
- elastiknn-benchmarks - Gradle project containing Scala code and infrastructure for benchmarking. 
- elastiknn-client-elastic4s - Gradle project containing a Scala client based on [Elastic4s](https://github.com/sksamuel/elastic4s).
- elastiknn-lucene - Gradle project containing custom Lucene queries implemented in Java.
- elastiknn-models - Gradle project containing custom similarity models implemented in Java.
- elastiknn-plugin - Gradle project containing the actual plugin implementation.
- elastiknn-testing - Gradle project containing Scala tests for all of the other Gradle subprojects.

The `lucene` and `models` sub-projects are implemented in Java for a few reasons:

1. It makes it easier to ask questions on the Lucene issue tracker and mailing list.
2. They are the most CPU-bound parts of the codebase. While Scala's abstractions are nicer than Java's, they sometimes 
   have a surprising performance cost (e.g., boxing).
3. It makes them more likely to be useful to other JVM developers. In particular the `models` project, which can be used
   to hash vectors and compute similarities in any JVM app.

### Build tools: Make and Gradle

Gradle manages the plugin and all of the JVM (i.e. Java and Scala) subprojects.

Make is used (arguably _abused_) to define command aliases with simple dependencies.
Make makes it relatively easy to run tests, generate docs, publish artifacts, etc. all from one file.
If anyone knows of a less hacky way to do this, I'm all ears.

### IDE

I recommend using IntelliJ Idea to work on the Gradle projects and Pycharm to work on the client-python project.

IntelliJ should immediately recognize the Gradle project when you open the `elastiknn` directory.

PyCharm can be a bit of a different story. 
You should first create a virtual environment in `client-python/venv`.
You can do this by running `make test/python`. Even if the tests fail, it will still create the virtual environment.
Then you should setup PyCharm to use the interpreter in `client-python/venv`. 

### Testing

Elastiknn has a fairly thorough test suite.

To run it, you'll first need to run `make run/cluster` or `make run/gradle` to start a local Elasticsearch server.

Then, run `make test/gradle` to run the Gradle test suite, or `make test/python` to run the smaller Python test suite.

### Debugging

You can attach IntelliJ's debugger to a local Elasticsearch process.
This can be immensely helpful when dealing with bugs or just figuring out how the code is structured.

First, open your project in IntelliJ and run the `Debug Elasticsearch` target (usually in the upper right corner).
Then just run `make run/debug` in your terminal.

Now you should be able to set and hit breakpoints in IntelliJ.

### Local Cluster

Use `make run/cluster` to run a local cluster with one master node and one data node (using docker-compose).
There are a couple parts of the codebase that deal with serializing queries for use in a distributed environment.
Running this small local cluster exercises those code paths.

### Benchmarking and Profiling

TODO

## Nearest Neighbors Search

Nearest neighbors search is a large topic. Some good places to start are:

- Chapter 3 of [Mining of Massive Datasets by Leskovec, et. al.](http://www.mmds.org/)
- Lectures 13-20 of [this lecture series from IIT Kharagpur](https://www.youtube.com/watch?v=06HGoXE6GAs&list=PLbRMhDVUMngekIHyLt8b_3jQR7C0KUCul&index=14)
- Assignment 1 of Stanford's [CS231n course](https://cs231n.github.io/)
- This work-in-progress literature review of [nearest neighbor search methods related to Elasticsearch](https://docs.google.com/document/d/14Z7ZKk9dq29bGeDDmBH6Bsy92h7NvlHoiGhbKTB0YJs/edit)
