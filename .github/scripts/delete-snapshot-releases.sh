#!/bin/bash
set -e

lookback=100
closed_prs=$(gh pr list --state closed --json number --jq '.[].number' --limit $lookback)
pr_release_tags=$(gh release list --limit $lookback | grep -E "PR[0-9]+-SNAPSHOT" | awk '{print $3}')

for pr in $closed_prs
do
  echo "Looking for releases for PR $pr..."
  if tag=$(echo "$pr_release_tags" | grep "PR$pr-SNAPSHOT")
  then
    gh release delete $tag --yes
    git push --delete origin $tag
  fi
done


#
#PRNUMS=$(hub pr list -s closed --format='%I ' --limit=10 --sort=updated)
#
#for N in $PRNUMS;
#do
#  TAGS=$(hub release | grep "PR$N-" || true)
#  for T in $TAGS;
#  do
#    echo "Deleting $T"
#    gh release delete "$T" --yes
#    git push --delete origin "$T"
#  done
#done
#
## Delete snapshots from master.
#TAGS=$(hub release | grep "MAIN[0-9]*-SNAPSHOT" || true)
#for T in $TAGS;
#do
#  gh release delete "$T" --yes
#  git push --delete origin "$T"
#done
