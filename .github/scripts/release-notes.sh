#!/usr/bin/env bash
# Prints the release notes for a tag: one "- subject" line per commit since the previous tag,
# leaving out merges, the "Release x.y.z" commit and commits that only change docs (Markdown
# files and docs/).
# The app shows these lines as "What's new". Usage: release-notes.sh <tag> [owner/repo]
set -euo pipefail

tag="$1"
repository="${2:-}"
previous="$(git describe --tags --abbrev=0 "$tag^" 2>/dev/null || true)"
range="${previous:+$previous..}$tag"

echo "## What's new"
echo
git log --no-merges --format='%H %s' "$range" | while read -r sha subject; do
  if [[ "$subject" =~ ^Release\ [0-9] ]]; then
    continue
  fi
  if ! git diff-tree --no-commit-id --name-only -r "$sha" | grep -qvE '(\.md$|^docs/)'; then
    continue
  fi
  echo "- $subject"
done

if [ -n "$previous" ] && [ -n "$repository" ]; then
  echo
  echo "**Full Changelog**: https://github.com/$repository/compare/$previous...$tag"
fi
