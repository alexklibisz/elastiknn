#!/usr/bin/env bash
# Usage: verify.sh
# Run from the repo root. Checks that the pinned Lucene matches the Lucene bundled with Elasticsearch,
# then compiles and runs unit tests. Prints a one-line verdict: LUCENE MISMATCH, COMPILE FAILED, UNIT FAILED, or UNIT OK.
set -uo pipefail
strip() { sed 's/\x1b\[[0-9;]*[A-Za-z]//g'; }
log=$(mktemp)
# The sbt server caches the build, so reload it in case build.sbt changed.
sbt -client reload >/dev/null 2>&1
PINNED=$(sed -nE 's/^lazy val LuceneVersion = "([^"]+)"/\1/p' build.sbt)
BUNDLED=$(sbt -client "elastiknn-plugin/dependencyTree" 2>&1 | strip | grep -oE 'org.apache.lucene:lucene-core:[0-9.]+' | grep -v "$PINNED" | head -1 | sed 's/.*://')
echo "lucene pinned=$PINNED bundled=${BUNDLED:-$PINNED}"
[ -z "$BUNDLED" ] || { echo "LUCENE MISMATCH"; exit 1; }
task jvmCompile > "$log" 2>&1 || { strip < "$log" | grep -E '\[error\]' | head -20; echo "COMPILE FAILED"; exit 1; }
task jvmUnitTest > "$log" 2>&1; rc=$?
strip < "$log" | grep -E 'FAILED|was not|Tests: ' | tail -10
[ $rc -eq 0 ] && echo "UNIT OK" || { echo "UNIT FAILED"; exit 1; }
