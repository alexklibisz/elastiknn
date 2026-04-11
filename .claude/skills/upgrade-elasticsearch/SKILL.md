---
name: upgrade-elasticsearch
description: Upgrade Elasticsearch end-to-end: bump versions, check Lucene, compile, unit test, open PR, monitor CI and fix failures.
argument-hint: "[target version, e.g. 8.18.5]"
---

Upgrade Elasticsearch to the version specified by the user. If no version is specified, run `gh release list --repo elastic/elasticsearch --limit 60` and pick the closest available version to the current one (i.e., the next patch or minor release — minimize the version jump). Follow these steps in order, fixing any issues before moving to the next step:

1. **Bump versions** in all six places:
   - `ElasticsearchVersion` in `build.sbt`
   - Image tag in `docker/Dockerfile`
   - Image tag and release download URL in `docs/pages/installation.md`
   - `version` file (format: `X.Y.Z.0`)
   - `elasticsearch` pin in `client-python/requirements.txt` — upgrade to the latest available version whose minor version does not exceed the new ES minor version. The Python client does **not** publish patch releases (e.g. there is no `8.18.6`), so check available versions first: `pip index versions elasticsearch` or check PyPI.
   - `Elastic4sVersion` in `build.sbt` — upgrade to the latest available `nl.gn0s1s:elastic4s-client-esjava` version whose minor version does not exceed the new ES minor version. Check available versions on Maven Central: `curl -s "https://repo1.maven.org/maven2/nl/gn0s1s/elastic4s-client-esjava_3/maven-metadata.xml" | grep '<version>'`

2. **Check if LuceneVersion needs updating.** ES ships with a bundled Lucene. If it changed, the old pinned version will show as "evicted" in the dependency tree:
   ```
   sbt -client "elastiknn-plugin/dependencyTree" | grep lucene
   ```
   If evicted, bump `LuceneVersion` in `build.sbt` to match.

3. **Verify compilation:** `task jvmCompile`. This compiles all modules including integration test sources (`compile; Test/compile`). Fix any issues before proceeding.

4. **Verify unit tests:** `task jvmUnitTest`. Fix any failures before proceeding.

Once all steps pass, open a PR with the title `Dependencies: upgrade Elasticsearch to <new version>` (e.g. `Dependencies: upgrade Elasticsearch to 8.18.5`).

5. **Monitor CI and fix failures.** After the PR is open, poll GitHub Actions until CI completes or a job fails:

   ```bash
   # Get the latest run ID for the PR branch
   gh run list --branch <branch-name> --limit 5

   # Watch a specific run (blocks until done, then prints summary)
   gh run watch <run-id>

   # If a job failed, get the logs
   gh run view <run-id> --log-failed
   ```

   For each failed job:
   - Read the failure output carefully to identify the root cause.
   - Fix the issue locally (edit code, push a new commit).
   - Wait for a new CI run to start on that push, then repeat the watch/log loop.
   - Continue until all jobs pass (green).

   Do not close or abandon the PR — iterate until CI is green.
