#!/usr/bin/env bash
# Usage: release.sh <expected-version> [backport-branch]
# Runs the Release workflow on main, or on the given backport branch with the backport option,
# then verifies the GitHub release and the PyPI package.
set -uo pipefail
V="$1"; B="${2:-main}"
git fetch -q origin
[ "$(git show "origin/$B:version")" = "$V" ] || { echo "$B version is $(git show "origin/$B:version"), expected $V"; exit 1; }
before=$(gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId')
if [ "$B" = "main" ]; then
  gh workflow run release.yml --ref main -f full-release=true
else
  gh workflow run release.yml --ref "$B" -f full-release=true -f backport=true
fi
until id=$(gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId') && [ "$id" != "$before" ]; do sleep 5; done
until [ "$(gh run view "$id" --json status -q .status 2>/dev/null)" = "completed" ]; do sleep 15; done
[ "$(gh run view "$id" --json conclusion -q .conclusion)" = "success" ] || { echo "RELEASE WORKFLOW FAILED run=$id"; gh run view "$id" --log-failed | tail -20; exit 1; }
gh release view "$V" --json url,isPrerelease,assets -q '"\(.url) prerelease=\(.isPrerelease) assets=\([.assets[].name]|join(","))"'
echo "latest=$(gh api repos/alexklibisz/elastiknn/releases/latest -q .tag_name)"
echo "pypi=$(curl -s -o /dev/null -w '%{http_code}' "https://pypi.org/project/elastiknn-client/$V/")"
