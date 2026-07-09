#!/usr/bin/env bash
# Wire this clone's git config to the repo's committed hooks. The dev-ledger commit-path gate and the
# gitleaks secret scan live in .githooks/ but stay inert until core.hooksPath points at them — so a
# fresh clone runs no gate until this is set. Run once per clone.
set -euo pipefail
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
echo "core.hooksPath -> .githooks (dev-ledger gate + secret scan now active for this clone)"
