#!/bin/bash
set -e

limit=100
closed_prs=$(gh pr list --state closed --json number --jq '.[].number' --limit $limit)
pr_release_tags=$(gh release list --limit $limit | grep -E "PR[0-9]+-SNAPSHOT" | awk '{print $3}')

# Loop over the closed PRs and delete any releases that correspond to a closed PR.
for pr in $closed_prs
do
  echo "Checking PR $pr"
  for tag in $(echo "$pr_release_tags" | grep "PR$pr-SNAPSHOT")
  do
    echo "Deleting release $tag"
    gh release delete "$tag" --yes
    git push --delete origin "$tag"
  done
done
