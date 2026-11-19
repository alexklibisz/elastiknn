---
name: weekly-maintenance
description: "Weekly unattended maintenance: merge green dependency PRs, upgrade to new Elasticsearch patch releases, and cut releases."
---

Run the weekly maintenance procedure below. It is designed to run unattended (see `.github/workflows/weekly-maintenance.yml`), so nobody is available to answer questions. When this procedure says to stop, open an issue instead of asking.

The helper scripts in `development/maintenance/` do the mechanical work. Prefer them over doing the same steps by hand. CI takes about 25 minutes, so `watch-ci.sh` and `release.sh` block for a long time; that is expected.

## Rules

- Never force push, delete branches other than merged PR branches, delete releases, or change workflow files.
- Merge PRs with `gh pr merge <number> --squash --delete-branch`, and only when every check is green.
- A failing CI run gets one rerun (`watch-ci.sh` does this). `PanamaFloatVectorOpsSpec` is a known source of flakes. If the rerun also fails, the failure is real.
- Never merge a PR that changes code beyond version numbers. Leave it open for a human to review.
- If anything goes wrong that this procedure doesn't cover, stop and open an issue (see "Report").

## 1. Merge dependency PRs

List open PRs from Scala Steward and Dependabot:

```bash
gh pr list --state open --json number,title,author,mergeable,statusCheckRollup \
  -q '.[] | select(.author.login == "app/alexklibisz-scala-steward" or .author.login == "app/dependabot")'
```

- Skip PRs that bump Elasticsearch itself; step 2 handles those. Close them with a comment if step 2 supersedes them.
- If a newer PR supersedes an older one for the same dependency, close the older one with a comment.
- Merge PRs whose checks are all green.
- For a PR with failing checks, run `development/maintenance/watch-ci.sh <head-branch>` after `gh run rerun <run-id> --failed`. Merge it if it passes. If it still fails, leave it open and note it in the report.
- For a PR with merge conflicts, leave it for next week. Scala Steward rebases its PRs on its next run; for Dependabot, comment `@dependabot rebase`.

Pull main after merging: `git checkout main && git pull`.

## 2. Upgrade Elasticsearch

Find the Elasticsearch patch releases newer than `ElasticsearchVersion` in `build.sbt` on the same minor line:

```bash
gh release list --repo elastic/elasticsearch --limit 60 --json tagName,isPrerelease -q '.[] | select(.isPrerelease | not) | .tagName'
```

Work through them in ascending order. For each version `V`:

1. `development/maintenance/bump-elasticsearch.sh V` from an up-to-date main.
2. Check `Elastic4sVersion` in `build.sbt` and the `elasticsearch` pin in `client-python/requirements.txt` as described in `skills/upgrade-elasticsearch/SKILL.md`, and bump them if needed.
3. `development/maintenance/verify.sh`. On `LUCENE MISMATCH`, bump `LuceneVersion` in `build.sbt` to the bundled version and run it again.
4. Commit with the message `Dependencies: upgrade Elasticsearch to V`, push, and open a PR into main with the same title. The body states the Lucene version and whether Elastic4s or the Python client changed.
5. `development/maintenance/watch-ci.sh upgrade-es-V`.
6. If CI passes and the PR only changes version numbers, merge it, then run `development/maintenance/release.sh V.0`.

Stop the Elasticsearch upgrades (but continue to step 3) when:

- The next release is a new minor or major version (e.g. 9.6.0). Do not open a PR for it; note it in the report. Minor upgrades have needed code changes before (e.g. 9.5.0), which need human review.
- Compile, unit tests, or CI fail. You may push fixes to the PR, but leave it open for review and don't merge it.

## 3. Release dependency updates

If step 1 merged at least one PR that changes the plugin or the Python client, and step 2 did not release anything, cut a release that bumps the 4th digit of the version (e.g. `9.5.5.1` to `9.5.5.2`):

1. From an up-to-date main, create branch `release/<new-version>`.
2. Update `version` and the release URLs in `docs/pages/installation.md`.
3. Commit as `Release <new-version>`, push, and open a PR with the same title.
4. CI doesn't run automatically for version-only changes, so trigger it: `gh workflow run ci.yaml --ref release/<new-version>`, then `development/maintenance/watch-ci.sh release/<new-version>`.
5. Merge the PR, then run `development/maintenance/release.sh <new-version>`.

Merges that only touch `docs/` or `.github/` (e.g. most Dependabot PRs) don't change what users install, so they don't warrant a release on their own.

## 4. Verify releases

For every release, `release.sh` prints the release URL, the latest tag, and the PyPI status. Every release must exist with the plugin zip attached and the PyPI status must be 200. Main releases must be `latest`.

## Report

Write a short summary to `$GITHUB_STEP_SUMMARY` (or print it, when running locally) with:

- PRs merged, closed, and left open, with reasons.
- Elasticsearch upgrades done, and any skipped (e.g. a new minor version available).
- Releases cut, with links.

If anything needs a human (a real CI failure, a new minor version, a PR with code changes, a failed release), open one issue titled `Weekly maintenance: <short description>` that links to the workflow run (`$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID`) and the relevant PRs. Before opening it, check `gh issue list --state open --search "Weekly maintenance in:title"` and comment on an existing issue instead of opening a duplicate.
