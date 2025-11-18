#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not a git repository: $SCRIPT_DIR"
  exit 1
fi

branch="$(git symbolic-ref --short HEAD || true)"
if [ -z "$branch" ]; then
  echo "No current branch"
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  git add -A
  git commit -m "chore: auto-push $(date '+%Y-%m-%d %H:%M:%S')"
fi

if git rev-parse --abbrev-ref --symbolic-full-name @{u} >/dev/null 2>&1; then
  git push
else
  git push -u origin "$branch"
fi

echo "Pushed $branch to $(git remote get-url --push origin)"
