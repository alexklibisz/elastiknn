#!/usr/bin/env bash
# Usage: watch-ci.sh <branch> [skip-run-id]
# Waits for the latest CI run on the branch (ignoring skip-run-id), and reruns failed jobs once.
# Prints PASS or FAIL with the run ID and the failing lines.
set -uo pipefail
B="$1"; SKIP="${2:-none}"
until id=$(gh run list --branch "$B" --workflow CI --limit 1 --json databaseId -q '.[0].databaseId') && [ -n "$id" ] && [ "$id" != "$SKIP" ]; do sleep 10; done
# Poll instead of `gh run watch`, which can exit early on transient API errors.
wait_done() {
  until [ "$(gh run view "$id" --json status -q .status 2>/dev/null)" = "completed" ]; do sleep 20; done
  until c=$(gh run view "$id" --json conclusion -q .conclusion 2>/dev/null) && [ -n "$c" ]; do sleep 10; done
  echo "$c"
}
failures() { gh run view "$id" --log-failed 2>&1 | sed 's/\x1b\[[0-9;]*[A-Za-z]//g' | grep -E 'FAILED|was not|##\[error\]' | cut -c1-250 | head -20; }
[ "$(wait_done)" = "success" ] && { echo "PASS run=$id"; exit 0; }
echo "First attempt failed run=$id:"
failures
until gh run rerun "$id" --failed; do sleep 30; done
sleep 30
[ "$(wait_done)" = "success" ] && { echo "PASS (after rerun) run=$id"; exit 0; }
echo "FAIL run=$id"
failures
exit 1
