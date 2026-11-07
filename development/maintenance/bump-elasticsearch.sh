#!/usr/bin/env bash
# Usage: bump-elasticsearch.sh <new-es-version>
# Run from the repo root on an up-to-date base branch. Creates branch upgrade-es-<version> and bumps the
# Elasticsearch version in build.sbt, docker/Dockerfile, docs/pages/installation.md, and the version file.
# Elastic4s, the Python client, and Lucene are checked separately (see skills/upgrade-elasticsearch).
set -euo pipefail
export NEW="$1"
export OLD=$(sed -nE 's/^lazy val ElasticsearchVersion = "([^"]+)"/\1/p' build.sbt)
export OLDV=$(cat version)
git checkout -q -b "upgrade-es-$NEW"
perl -pi -e 's/ElasticsearchVersion = "\Q$ENV{OLD}\E"/ElasticsearchVersion = "$ENV{NEW}"/' build.sbt
perl -pi -e 's/elasticsearch:\Q$ENV{OLD}\E/elasticsearch:$ENV{NEW}/' docker/Dockerfile
perl -pi -e 's/elasticsearch:\Q$ENV{OLD}\E/elasticsearch:$ENV{NEW}/; s/\Q$ENV{OLDV}\E/$ENV{NEW}.0/g' docs/pages/installation.md
echo "$NEW.0" > version
git diff --stat
