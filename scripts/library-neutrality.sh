#!/usr/bin/env bash
# Library neutrality gate — the `claim-algebra/` module (the algebra + calculus library) must carry
# NO domain vocabulary (credit / product / experiment / model), so it can be excised and reused
# across domains (audit, tax, advisory, deals — not only credit). This is the definition of DONE for
# the domain-neutral-library effort; see docs/claim-algebra/library-portability-plan.md.
#
# Exit 0 iff the library is neutral.
#   bash scripts/library-neutrality.sh              # full check (all three conjuncts)
#   bash scripts/library-neutrality.sh --no-build   # skip Conjunct 2 (used by the in-build `sbt check` task)
set -uo pipefail
cd "$(dirname "$0")/.."
NOBUILD="${1:-}"

fail=0

echo "== Conjunct 1: no domain vocabulary in claim-algebra/src =="
# TWO classes, so a PascalCase identifier that LEFT the library (Routing/Desk/CreditKind/...) matches
# the SYMBOL, not an English word — κ̂'s neutral "routing" prose must NOT trip the gate:
#   VOCAB — case-INSENSITIVE credit/product/experiment/model words + the product phrasings of
#           "operator" (an "operator" alone is a legitimate algebra term, so only the UI phrasings).
#   IDENT — case-SENSITIVE credit identifiers (PascalCase symbols) + the concrete Kind values.
# Deliberately EXCLUDED as neutral (a list including these can never go green): bare
# verify/verification (the gate's verify axis), bare audit (neutral provenance prose), bare signature
# (the accept term), bare Kind (the neutral marker `trait Kind` stays), and bare operator.
VOCAB='credit|covenant|EBITDA|leverage|\bmargin\b|\bdebt\b|\bdeal\b|workbench|operator-facing|operator-view|slide|the firm|EDGAR|experiment|\bCWS\b|falsif|\bLLM\b|Anthropic|OpenAI'
IDENT='CreditKind|\bDesk\b|\bRouting\b|deskFor|DataQuality|CreditPolicy|DealLead|Kind\.(Extraction|Definitional|TemporalRetraction|Verification)'
hits=$( { grep -rIniE "$VOCAB" claim-algebra/src; grep -rInE "$IDENT" claim-algebra/src; } | sort -u )
if [ -n "$hits" ]; then
  n=$(printf '%s\n' "$hits" | grep -c .)
  echo "  FAIL — $n domain-vocabulary hit(s):"
  printf '%s\n' "$hits" | sed 's|claim-algebra/src/|    |'
  fail=1
else
  echo "  ok — no domain vocabulary in the library"
fi

if [ "$NOBUILD" = "--no-build" ]; then
  echo "== Conjunct 2: standalone build+test — SKIPPED (--no-build) =="
else
  echo "== Conjunct 2: claim-algebra builds + tests standalone (no other module needed) =="
  if sbt "claimAlgebra/compile; claimAlgebra/test" >/tmp/library-neutrality-build.log 2>&1; then
    echo "  ok — standalone compile + test green"
  else
    echo "  FAIL — standalone build/test failed (see /tmp/library-neutrality-build.log)"
    tail -5 /tmp/library-neutrality-build.log | sed 's/^/    /'
    fail=1
  fi
fi

echo "== Conjunct 3: claim-algebra dependencies stay neutral (no cats-effect / SDK) =="
# The pure/effectful seam is structural — the library must not gain cats-effect or an SDK dep.
if grep -qE 'catsEffect|anthropic|openai|jackson' <(sed -n '/lazy val claimAlgebra/,/^lazy val /p' build.sbt); then
  echo "  FAIL — claim-algebra libraryDependencies name an effectful/SDK dep"
  fail=1
else
  echo "  ok — cats-core + algebra only"
fi

echo
if [ $fail -eq 0 ]; then echo "NEUTRAL ✓"; else echo "NOT NEUTRAL ✗ — see docs/claim-algebra/library-portability-plan.md"; fi
exit $fail
