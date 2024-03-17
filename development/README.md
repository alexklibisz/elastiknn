# Elastiknn Development

This document includes some notes about development of Elastiknn.

## Local Development Setup

You need at least the following software installed: git, Java 21, Python 3.10, SBT, docker, docker compose, and [task](https://taskfile.dev).
We're assuming the operating system is Linux or MacOS.
There might be other software which is missing. 
If so, please submit an issue or PR.

## AWS Development Setup

The [aws](./aws) directory contains [Packer](https://www.packer.io/) and [Terraform](https://www.terraform.io/) files for creating a development instance in AWS.   

## Development

### Run a local Elasticsearch instance with the plugin installed

Once you have the prerequisites installed, clone the project and run:

```
task jvmRunLocal
```

This starts a local instance of Elasticsearch with the plugin installed. 
It can take about five minutes the first time you run it. 

Once you see "EXECUTING", you should open another shell and run `curl localhost:9200`.
You should see the usual Elasticsearch JSON response containing the version, cluster name, etc.

### Project Structure

Elastiknn currently consists of several subprojects managed by Task and Gradle:

- client-python - Python client.
- elastiknn-api4s - SBT project containing Scala case classes that model the Elastiknn API.
- elastiknn-client-elastic4s - SBT project containing a Scala client based on [Elastic4s](https://github.com/sksamuel/elastic4s).
- elastiknn-lucene - SBT project containing custom Lucene queries implemented in Java.
- elastiknn-models - SBT project containing custom similarity models implemented in Java.
- elastiknn-plugin - SBT project containing the actual plugin implementation.
- elastiknn-testing - SBT project containing Scala tests for all the other Gradle subprojects.
- ann-benchmarks - Python project for benchmarking based on [erikbern/ann-benchmarks](https://github.com/erikbern/ann-benchmarks).

The `lucene` and `models` sub-projects are implemented in Java for a few reasons:

1. It makes it easier to ask questions on the Lucene issue tracker and mailing list.
2. They are the most CPU-bound parts of the codebase. While Scala's abstractions are nicer than Java's, they sometimes 
   have a surprising performance cost (e.g., boxing).

### Build tools: Task and SBT

SBT manages the plugin and all the Java and Scala subprojects.

Task is used to define command aliases with simple dependencies.
This makes it relatively easy to run tests, generate docs, publish artifacts, etc. all from one file.

### IDE

I recommend using IntelliJ Idea to work on the Gradle projects and Pycharm to work on the client-python project.

Install the IntelliJ Scala plugin, and then IntelliJ will recognize the SBT project when you open the `elastiknn` directory.

PyCharm can be a bit of a different story. 
You should first create a virtual environment in `client-python/venv`.
You can do this by running `task pyVenv`. Even if the tests fail, it will still create the virtual environment.
Then you should setup PyCharm to use the interpreter in `client-python/venv`. 

### Testing

Elastiknn has a fairly thorough test suite.

To run it, you'll first need to run `task dockerRunTestingCluster` or `task jvmRun` to start a local Elasticsearch server.

Then, run `task jvmUnitTest` to run the SBT test suite, or `task pyTest` to run the smaller Python test suite.

### Debugging

You can attach IntelliJ's debugger to a local Elasticsearch process.
This can be immensely helpful when dealing with bugs or just figuring out how the code is structured.

First, open your project in IntelliJ and run the `Debug Elasticsearch` target (usually in the upper right corner).
Then just run `task jvmRunLocalDebug` in your terminal.

Now you can set and hit breakpoints in IntelliJ.
To try it out, open the RestPluginsAction.java file in IntelliJ, add a breakpoint in the `getTableWithHeader` method, and run `curl localhost:9200/_cat/plugins`.
IntelliJ should stop execution at your breakpoint.

### Local Cluster

Use `task dockerRunTestingCluster` to run a local cluster with one master node and one data node (using docker compose).
There are a couple parts of the codebase that deal with serializing queries for use in a distributed environment.
Running this small local cluster exercises those code paths.

### Benchmarking and Profiling

See ann-benchmarks/README.md

### Miscellaneous Quirks

- To run Elasticsearch on Linux, you need to increase the `vm.max_map_count` setting. [See the Elasticsearch docs.](https://www.elastic.co/guide/en/elasticsearch/reference/current/vm-max-map-count.html)
- To run ann-benchmarks on MacOS, you might need to `export OBJC_DISABLE_INITIALIZE_FORK_SAFETY=YES`. [See this Stackoverflow answer.](https://stackoverflow.com/a/52230415) 
- If you're running on MacOS 13.x (Ventura), the operating system's privacy settings might block `task jvmRunLocal` from starting. One solution is to go to System Settings > Privacy & Security > Developer Tools, and add and check your terminal (e.g., iTerm) to the list of developer apps. If that doesn't work, see this thread for more ideas: https://github.com/elastic/elasticsearch/issues/91159.
- When running tests from Intellij, you might need to add `--add-modules jdk.incubator.vector` to the VM options.

## Nearest Neighbors Search

Nearest neighbors search is a large topic. Some good places to start are:

- Chapter 3 of [Mining of Massive Datasets by Leskovec, et. al.](http://www.mmds.org/)
- Lectures 13-20 of [this lecture series from IIT Kharagpur](https://www.youtube.com/watch?v=06HGoXE6GAs&list=PLbRMhDVUMngekIHyLt8b_3jQR7C0KUCul&index=14)
- Assignment 1 of Stanford's [CS231n course](https://cs231n.github.io/)
- This work-in-progress literature review of [nearest neighbor search methods related to Elasticsearch](https://docs.google.com/document/d/14Z7ZKk9dq29bGeDDmBH6Bsy92h7NvlHoiGhbKTB0YJs/edit)
