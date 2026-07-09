#!/usr/bin/env bash
# The lab's mechanical check — every language that builds the system (see ledger/languages).
# The dev-ledger gate runs this (check_command → ledger/check.sh) whenever a staged file
# matches a declared build language. Both halves must pass for `repo-check` to sign; a
# failure in either blocks the commit and records a refuted entry.
#   - Scala backend:  sbt check (scalafmt, scalafix, library-neutrality, the suites)
#   - TypeScript UI:  prettier --check, eslint --max-warnings 0, tsc --noEmit, vitest run
# Hermetic: the frontend "live" suite stubs EventSource/fetch, so nothing hits the network.
set -euo pipefail
cd "$(dirname "$0")/.."
sbt -batch -Dsbt.color=false check
( cd reasoning-society/frontend && npm run check )
