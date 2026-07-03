#!/usr/bin/env bash
# Library neutrality gate — the `claim-algebra/` module (the algebra + calculus library) must carry
# NO domain vocabulary (credit / product / experiment), so it can be excised and reused across
# domains (audit, tax, advisory, deals — not only credit). This is the definition of DONE for the
# domain-neutral-library effort; see docs/claim-algebra/library-portability-plan.md.
#
# Exit 0 iff the library is neutral. Run:  bash scripts/library-neutrality.sh
set -uo pipefail
cd "$(dirname "$0")/.."

fail=0

echo "== Conjunct 1: no domain vocabulary in claim-algebra/src =="
# Credit / product / experiment terms + the concrete taxonomy identifiers that must LEAVE the
# library (to the `credit` module) + the credit example terms currently in the tests.
# Deliberately EXCLUDED as neutral (a naive list including these can never go green): bare
# verify/verification (the gate's verify axis, Verifier/Claim), bare audit (neutral provenance
# prose), bare signature (the accept term), and bare `Kind` (the neutral marker `trait Kind` stays).
PATTERN='credit|covenant|EBITDA|leverage|\bmargin\b|\bdebt\b|\bdeal\b|workbench|operator|slide|the firm|EDGAR|experiment|\bCWS\b|falsif|CreditKind|Desk|Routing|deskFor|DataQuality|CreditPolicy|DealLead|Kind\.(Extraction|Definitional|TemporalRetraction|Verification)'
hits=$(grep -rIniE "$PATTERN" claim-algebra/src || true)
if [ -n "$hits" ]; then
  n=$(printf '%s\n' "$hits" | grep -c .)
  echo "  FAIL — $n domain-vocabulary hit(s):"
  printf '%s\n' "$hits" | sed 's|claim-algebra/src/|    |'
  fail=1
else
  echo "  ok — no domain vocabulary in the library"
fi

echo "== Conjunct 2: claim-algebra builds + tests standalone (no other module needed) =="
if sbt "claimAlgebra/compile; claimAlgebra/test" >/tmp/library-neutrality-build.log 2>&1; then
  echo "  ok — standalone compile + test green"
else
  echo "  FAIL — standalone build/test failed (see /tmp/library-neutrality-build.log)"
  tail -5 /tmp/library-neutrality-build.log | sed 's/^/    /'
  fail=1
fi

echo "== Conjunct 3: claim-algebra dependencies stay neutral (no cats-effect / SDK) =="
# The pure/effectful seam is structural — the library must not gain cats-effect or an SDK dep.
# (Slice D wires this precisely against the build; here it is a smoke check of the module block.)
if grep -qE 'catsEffect|anthropic|openai|jackson' <(sed -n '/lazy val claimAlgebra/,/^lazy val /p' build.sbt); then
  echo "  FAIL — claim-algebra libraryDependencies name an effectful/SDK dep"
  fail=1
else
  echo "  ok — cats-core + algebra only"
fi

echo
if [ $fail -eq 0 ]; then echo "NEUTRAL ✓"; else echo "NOT NEUTRAL ✗ — see docs/claim-algebra/library-portability-plan.md"; fi
exit $fail
