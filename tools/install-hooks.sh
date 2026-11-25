#!/usr/bin/env bash
# tools/install-hooks.sh - Configures git to use repository version-controlled hooks
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

chmod +x tools/pre-commit-check.sh tools/pre-pr-test.sh .githooks/*

git config core.hooksPath .githooks
echo ">> [HOOKS] Git core.hooksPath set to .githooks"
echo ">> [HOOKS] Pre-commit and pre-push regression hooks are now ACTIVE."
