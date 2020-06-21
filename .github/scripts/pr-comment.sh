#!/bin/bash
set -e

BODY=$(cat $1)
BRANCH=$(git rev-parse --abbrev-ref HEAD)
NUMS=$(hub pr list -s open -f '%I %H%n' | grep $BRANCH | cut -d' ' -f1 || true)
DATA=$(jq --arg body "$BODY" '{"body": $body}' <<< {})

for N in $NUMS;
do
    echo "Commenting on PR #${N}"
    curl -H "Authorization: token ${GITHUB_TOKEN}" \
         -X POST -d "$DATA" \
         "https://api.github.com/repos/alexklibisz/elastiknn/issues/${N}/comments"
    echo $n
done
