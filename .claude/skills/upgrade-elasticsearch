Upgrade Elasticsearch to the version specified by the user (or infer it from context). Follow these steps in order, fixing any issues before moving to the next step:

1. **Bump versions** in all four places:
   - `ElasticsearchVersion` in `build.sbt`
   - Image tag in `docker/Dockerfile`
   - Image tag and release download URL in `docs/pages/installation.md`
   - `version` file (format: `X.Y.Z.0`)

2. **Check if LuceneVersion needs updating.** ES ships with a bundled Lucene. If it changed, the old pinned version will show as "evicted" in the dependency tree:
   ```
   sbt -client "elastiknn-plugin/dependencyTree" | grep lucene
   ```
   If evicted, bump `LuceneVersion` in `build.sbt` to match.

3. **Verify compilation:** `task jvmCompile`. Fix any issues before proceeding.

4. **Verify unit tests:** `task jvmUnitTest`. Fix any failures before proceeding.

5. **Verify integration tests:** `task dockerRunTestingCluster` then `task jvmIntegrationTest`. Fix any failures.

Once all steps pass, open a PR.
