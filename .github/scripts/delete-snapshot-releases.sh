#!/bin/bash
set -e

# Delete snapshots from closed PRs.
PRNUMS=$(hub pr list -s closed --format='%I ' --limit=10 --sort=updated)

for N in $PRNUMS;
do
  TAGS=$(hub release | grep "PR$N-" || true)
  for T in $TAGS;
  do
    echo "Deleting $T"
    gh release delete "$T" --yes
    git push --delete origin "$T"
  done
done

# Delete snapshots from master.
TAGS=$(hub release | grep "MAIN[0-9]*-SNAPSHOT" || true)
for T in $TAGS;
do
  gh release delete "$T" --yes
  git push --delete origin "$T"
done
