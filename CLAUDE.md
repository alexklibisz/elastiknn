# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

### Task CLI

Use the `task` CLI — read Taskfile.yaml for all available tasks and to understand how they work.

### SBT

The underlying tool for most tasks is `sbt`, specifically `sbt -client` (thin client connecting to a persistent sbt server).
Sometimes you need to use it directly rather than using 

**Running a single test class:**
```bash
sbt -client "elastiknn-plugin/testOnly com.klibisz.elastiknn.query.SomeSpec"
```

### Integration Testing

Integration tests require a Docker cluster (see `dockerRunTestingCluster` / `dockerStopTestingCluster` tasks in Taskfile.yaml).

### Java Version

The project uses Java 21 (see `.tool-versions`). Manage via asdf. Building with a newer JDK will embed that version in `plugin-descriptor.properties`, causing the plugin install to fail in the Docker container (which runs ES's bundled JDK).

## Project Structure

The project has seven SBT subprojects (Scala 3.3.6):

| Module | Role |
|--------|------|
| `elastiknn-api4s` | API types: (`Vec`, `Mapping`, `NearestNeighborsQuery`, etc.) XContent serialization |
| `elastiknn-models` | LSH model implementations and vector similarity math (Java + Panama SIMD) |
| `elastiknn-lucene` | Custom Lucene queries (`MatchHashesAndScoreQuery`) and field types |
| `elastiknn-plugin` | ES plugin: mappers, query builders, score functions |
| `elastiknn-plugin-integration-tests` | Full end-to-end tests against a live ES cluster |
| `elastiknn-client-elastic4s` | Scala client library using elastic4s |
| `elastiknn-jmh-benchmarks` | JMH microbenchmarks |

Dependency order: `api4s` ← `models` ← `lucene` ← `plugin` ← `integration-tests`

## Key Version Variables (build.sbt)

```scala
ElasticsearchVersion  // must match docker/Dockerfile and version file
LuceneVersion         // must match the Lucene bundled in the ES release
ElastiknnVersion      // read from the `version` file (e.g. 8.18.4.0)
```

When upgrading ES, update all four: `build.sbt`, `docker/Dockerfile`, `docs/pages/installation.md`, and `version`. Also check whether the bundled Lucene version changed (`sbt elastiknn-plugin/dependencyTree | grep lucene`).

## Dependency Upgrades

Use `/upgrade-elasticsearch` to perform an Elasticsearch upgrade end-to-end.

Other JVM dependencies are mostly handled by Scala Steward via automated PRs (see git log for the pattern).

## SIMD / Panama Vector API

`PanamaFloatVectorOps` uses `jdk.incubator.vector` for SIMD-accelerated dot product, cosine, L1, and L2 computations. It is enabled at runtime via the `elastiknn.jdk-incubator-vector.enabled` setting. The sbt server and ES container both need `--add-modules jdk.incubator.vector` (already configured in `.sbtopts` and `build.sbt`).

SIMD `reduceLanes(ADD)` can produce slightly different floating-point results before vs. after JIT compilation — relevant when writing determinism tests.

## CI

`.github/workflows/ci.yaml` runs: lint → compile → assemble → unit tests → start Docker cluster → integration tests. Scala compiler is strict (`CiMode`) when `CI=true`; locally it uses `DevMode` (relaxed).
