# The verify-results check — design and outcome

**Status:** shipped (2026-07-10). The four crown-jewel tables and the clm-0015 two-lies split are
signed from committed receipts; clm-0025 was investigated and corrected without a body-digest — see
"clm-0025 — outcome" at the end.
**Date:** 2026-07-10.
**Goal:** discharge the six crown-jewel fallible-oracle findings mechanically, and get a
reproducibility harness a public reader can run — from one small check.

## The idea being evaluated

The six results-and-findings claims in the dev-ledger are `clm-0014, 0023, 0024, 0025, 0026,
0032`. Each carries a headline number, and each of those numbers was computed from an archived,
per-game record the `Archiver` slice wrote at run time. A small check that recomputes every
headline table from its archive and compares to the claimed number would discharge those claims
mechanically, and double as the reproducibility harness the public repo wants. That is the
premise. It survives scrutiny, with two adjustments and one decision.

## Receipts confirmed

The archives are on disk and machine-readable. Every game log opens with a header line the check
can parse without touching the transcript body:

```
# arm=seam-open cell=dev target=apple seed=-1584252127 outcome=Abstain signed=- signPath=-
```

| Archive dir | Stamp | Games (files) | Discharges |
|---|---|---|---|
| `results/composed-cell-…` | `e4666df175ff43e3` | 104 | clm-0014 (seam-gated vs seam-open) |
| `results/reveal-the-set-…` | `09b93e3e54d0f1f1` | 126 | clm-0032 (A withheld vs B revealed) |
| `results/stronger-closer-sonnet-…` | `09b93e3e54d0f1f1` | 192 | clm-0023 (W/S1/D, Sonnet) |
| `results/stronger-closer-opus-…` | `09b93e3e54d0f1f1` | 192 | clm-0023 (W/S1/D, Opus) |

## Two mechanism facts that reshape the build

Reading the gate end to end (`ledger/gate.py`, `ledger/check.sh`) turns this from a side script
into two concrete adjustments.

**Fact 1 — the archives are gitignored, so a check that reads them is not reproducible off this
machine.** `results/` is local-only. Wired into the commit gate, a check that reads those dirs
fail-closes everywhere they do not exist — CI, a fresh clone, the public derivative — and blocks
every future commit. The fix is also what makes the harness real: **distill each archive to a
committed digest** — one line per game (`arm target seed outcome signPath`), no transcript bodies.
Roughly 600 lines, redaction-trivial (the logs are twenty-questions games about *apple*, *tree*,
*cup*), and a public reader can then re-derive `5/62` from committed data without re-running a
model. The essay already claims "everything above is reproducible"; committing the digests makes
that literally true rather than aspirational.

**Fact 2 — the gate runs exactly one check and signs by sweeping runnable claims.** `gate.py` runs
`ledger/check.sh` (`sbt check` + the frontend `npm run check`) and signs any unverified claim whose
`check` is in `{repo-check, scala-check, scala-suite, typecheck}` when it passes. There is no
per-claim script dispatch. So the clean form is not a standalone script that "discharges" anything
— it is **a munit suite inside `sbt check`** that recomputes each table from the committed digests,
reusing the repo's own `ProportionDiff` (Newcombe) and Wilson `Rate` so the recompute cannot drift
from what the report printed. The existing gate then signs the re-asserted claims for free, and
fail-closed falls out: a missing digest fails the test.

## What a recompute can honestly sign — the crux

A recompute verifies that the *numbers reproduce from the receipts*. It does not verify the
*interpretation*. clm-0014, clm-0023, and clm-0032 are tables (`0/52`, `0/64`, `5/62`) and recompute
end to end. But:

- **clm-0024** ("capability under degradation is not gated by tier") is a *reading* of clm-0023's
  numbers, not a table.
- **clm-0026** ("the null is a joint channel-plus-decoder ceiling") is an *arithmetic* recompute —
  the capacity formula and the confidence interval — not an archive one, and its force is an
  interpretation.
- **clm-0025** ("every resurrected win preceded by a demoted quorum") needs the log *bodies*, not
  the headers, so it needs a heavier digest.

If the check signs the findings as written, it signs more than it checked — the one move this ledger
exists to forbid.

## Proposed design

The check discharges **six narrow claims** — *"finding X's headline table reproduces exactly from
archive stamp S"* — and the interpretive sentences stay as **unverified assertions**: kind
`assertion`, status `unverified` — *not* kind `testimony`. The distinction matters for the ledger's
hygiene. Testimony can never be signed by constitution; an assertion can gain a check later (clm-0024
is dischargeable in principle by a future cross-tier replication). Mis-kinding the interpretations as
testimony would shut a door the schema deliberately leaves open.

| Finding | What recomputes | First pass? |
|---|---|---|
| clm-0014 composed-cell | table from the `composed-cell` digest | yes |
| clm-0023 stronger-closer | table from the `sonnet` + `opus` digests | yes |
| clm-0032 reveal-set | table plus the Newcombe interval from the `reveal-the-set` digest | yes |
| clm-0026 capacity | the arithmetic — formula and interval, no archive | yes (separate check family) |
| clm-0024 tier-finding | interpretation of clm-0023's numbers | stays an unverified assertion |
| clm-0025 demotion-mechanism | needs the log bodies (heavier digest) | investigated → corrected, no body-digest (see end) |

So four discharged in pass one, two deliberately left as unverified assertions with the reason
recorded.

Mechanically:

1. Distill the four archives to committed digests (header fields only), **committing the distiller
   alongside** so the receipts have re-derivable provenance, and record a per-archive distillation
   claim (digest D distilled from stamp S, N games) discharged at distill time on the machine that
   holds the archives. A small demotion-event digest follows for clm-0025.
2. Add a munit suite in the reasoning-society module that reads each digest, recomputes the table and
   interval reusing `ProportionDiff` / `Rate`, and asserts each equals the published number. Each
   narrow claim **enumerates its exact tuple** — counts per arm, outcome, and signPath — so
   "reproduces exactly" is a closed set, not a vibe about a table. Fail-closed on a missing or
   tampered digest.
3. It runs inside `sbt check`, so the existing gate signs the re-asserted narrow claims (`check:
   "scala-check"`) on the next commit. Each original (clm-0014/0023/0032/0026) splits into **two
   successors** — the narrow table-claim that signs, and the interpretive claim that persists
   unverified — so no interpretive content evaporates in the split. **Note the immutability
   constraint:** these four originals were committed live in prior commits, so the audit's `immutable`
   check forbids retiring them to trace (that would rewrite a committed line). The split is therefore
   **supersede-in-place** — both successors carry `supersedes: <original>`, and the originals stay
   live-but-superseded (inert), which is exactly what the gate itself does when it signs.
4. The composed cell's +1 win accounting (5 resurrected, 4 attrition) is recomputable from headers
   alone by pairing across arms, so pass one asserts it; only the demoted-quorum *mechanism* behind
   it needs clm-0025's body digest.

Because this both signs ledger claims and lands digests in the public derivative, it is
verdict-shaped and outward-facing — the signable-scope framing, the supersede-vs-retire call, and the
distillation-discharge model get a fresh-context pressure test immediately before the signing commit.

## Decision — resolved

The **narrow-claims framing** is adopted, with the refinements above (explicit split; kind stays
`assertion`, not `testimony`; each narrow claim enumerates its exact tuple; distiller and per-archive
provenance committed). **First pass** is the four listed (clm-0014, 0023, 0032, 0026); **clm-0025**
was the scheduled follow-up, since investigated and corrected — see the next section.

**One structural correction landed during the build:** the pressure test (below) showed the
two-successor split double-counts the UNVERIFIED map, since immutability keeps each original live
regardless. Shipped instead as **one `about`-linked signed claim per finding** (clm-0044–0047,
`about` clm-0014/0023/0032/0026), the originals left standing as the unverified interpretive record.
The provenance claims (clm-0039–0042) are `check:"none"` — a runnable check would sign
"distilled from archive" on a check that never reads the gitignored archive (a fail-open the test
caught) — and the signed text cites the receipt name + body-sha256, since the config stamp
`09b93e3e…` is shared across three archives and is not unique.

## clm-0025 — outcome (investigated, corrected, no body-digest)

clm-0025 was the scheduled pass-two follow-up. A read-only investigation of the archived seam-gated
bodies settled it without a body-digest — and refuted half of it.

**The log-body format is parseable.** `OracleSweep.renderLog` emits one line per event: candidates
always quoted (`asserts "spoon"`, `*** SIGN "spoon" ***`, `GUESS "spoon" = YES`), the demotion as
`abstain — unconfirmed — … backed by N` (N ∈ {1,2,3}, so the count survives the 48-char reason
truncation), and the abstain categories (`no hypothesis` / `unconfirmed` / `ambiguous` / `conflict` /
`inconclusive`). So a body-digest was buildable.

**But the investigation refuted clm-0025's attrition half.** Split verdict:

- *Resurrection* — CONFIRMED. All 6 gated wins reach a demoted multi-backer quorum (backed by 2, then
  3) on the winning candidate before the oracle-confirmed SIGN. In the seam-open arm that 2-backer
  quorum would have signed and stopped; gating demotes it and the game survives to oracle confirmation.
- *Attrition* — REFUTED as worded. The 4 attrition games have **zero** demoted quorums (no `backed by
  N`); they abstain on **ambiguity** (`rival hypotheses, no single leader`) as the cohort diverges
  into rival phrasings (dog/domestic dog, shoe/A shoe or boot, three toy phrasings). The 2-backer
  quorum lived in the *open* arm. clm-0025's "every attrition reached a demoted quorum that failed to
  oracle-confirm" conflated the open-arm quorum with the gated body. Gating's real cost here is
  phrasing divergence into an ambiguity block, not a quorum that failed to confirm.

Recorded as **clm-0049** (refutation, `about` clm-0025) + **clm-0050** (corrected mechanism,
`supersedes` clm-0025, so the false wording leaves the live board without violating immutability).

**Decision: no body-digest built.** The essay's Finding-2 numbers are all header-level — 0 fail-open
and the `+1` are signed (clm-0044), and "two of the gated wins were lies in the open design" (spoon,
pencil) is a pure header fold, now checked in `ResultsReceiptsSuite` and signed (**clm-0051 →
clm-0053**). The body-digest would only mechanically verify the illustrative demote→confirm event
trace — whose interpretation stays unverified regardless — at the cost of a fragile text parser
(candidate attribution, truncation edges). Low value per parser risk, so it was declined. If the talk
wants spoon's trace as committed data, commit a curated demote→confirm extract as a plain doc, with no
suite or signing machinery.
