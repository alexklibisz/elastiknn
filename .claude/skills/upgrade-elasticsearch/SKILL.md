---
name: upgrade-elasticsearch
description: Upgrade Elasticsearch end-to-end: bump versions, check Lucene, compile, unit test, integration test, open PR.
argument-hint: "[target version, e.g. 8.18.5]"
---

Upgrade Elasticsearch to the version specified by the user. If no version is specified, run `gh release list --repo elastic/elasticsearch --limit 60` and pick the closest available version to the current one (i.e., the next patch or minor release — minimize the version jump). Follow these steps in order, fixing any issues before moving to the next step:

1. **Bump versions** in all six places:
   - `ElasticsearchVersion` in `build.sbt`
   - Image tag in `docker/Dockerfile`
   - Image tag and release download URL in `docs/pages/installation.md`
   - `version` file (format: `X.Y.Z.0`)
   - `elasticsearch` pin in `client-python/requirements.txt` — must match the new ES version exactly (e.g. `elasticsearch==8.18.6`)
   - `Elastic4sVersion` in `build.sbt` — upgrade to the latest available `nl.gn0s1s:elastic4s-client-esjava` version whose minor version does not exceed the new ES minor version. Check available versions on Maven Central: `curl -s "https://repo1.maven.org/maven2/nl/gn0s1s/elastic4s-client-esjava_3/maven-metadata.xml" | grep '<version>'`

2. **Check if LuceneVersion needs updating.** ES ships with a bundled Lucene. If it changed, the old pinned version will show as "evicted" in the dependency tree:
   ```
   sbt -client "elastiknn-plugin/dependencyTree" | grep lucene
   ```
   If evicted, bump `LuceneVersion` in `build.sbt` to match.

3. **Verify compilation:** `task jvmCompile`. Fix any issues before proceeding.

4. **Verify unit tests:** `task jvmUnitTest`. Fix any failures before proceeding.

5. **Verify integration tests:** `task dockerRunTestingCluster` then `task jvmIntegrationTest`. Fix any failures.

Once all steps pass, open a PR with the title `Dependencies: upgrade Elasticsearch to <new version>` (e.g. `Dependencies: upgrade Elasticsearch to 8.18.5`).
