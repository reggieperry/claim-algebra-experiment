# The composed cell — experiment specification

**Run-ready. One cell, not a rerun: the fourth corner of the endgame × seam grid, measured within-run.** 2026-07-09. Consolidates the committee-reconciled build plan (`fallible-oracle-composed-cell-plan.md`, verdict PROCEED_WITH_CHANGES, four lenses grounded against source) plus two review additions: the Run A `BackerQuorum` pre-pull and mandatory event-log archiving. This document is the pre-registration; it is complete before the run and the run is read against it.

---

## Scope — what runs and what does not

The grid has two axes, endgame version × seam configuration, and three corners are already measured: un-improved × open (the original sweep), un-improved × gated (Run B, which commit lineage places before the improvement commit), improved × open (Run A, the coupling result). The fourth corner — **improved × gated** — has never been observed under any conditions. This experiment measures it, with the improved × open corner rerun in the same invocation as internal control.

Nothing else reruns. The redundancy/correlation curve, the Theorem 6.7 suite, E3, E0, and the degraded-oracle cells all stand as measured. Jurisdiction: one new entry point (`RunComposedCell`), 104 live games (52 per arm), and the single pre-registered growth path in §Pre-registration item 2. No thresholds change anywhere; no prior arm is re-adjudicated.

## The question

Two halves with different epistemic status, per the committee's reframe:

- **Fail-open (deductive).** At a perfect oracle with the seam gated, both enumerated sign paths are shut: `C` (2-backer corroboration) is dropped by the flag, and `O` cannot confirm a wrong candidate when every answer is true. Gated fail-open is structurally ~0, bounded only by an unenumerated third sign path. The run confirms the deduction against the code as it exists.
- **Wins (open).** Gated wins are structurally ≤ open wins: removing the standalone `C` path re-routes those winners to the oracle within budget, exposed longer to rivals. Whether the improved society's wins survive gating is the question the cell exists to answer, not a prediction. The 0.08 prior from Run B bounds it (gating at a perfect oracle does not zero wins) without settling it, since Run B ran the un-improved endgame.
  > **Superseded by measurement (2026-07-09, see the fourth cell in `fallible-oracle-results.md`).** The ≤ prediction was REFUTED: gated wins *rose* (6/52 vs 5/52). The argument counted re-routed winners but not *resurrected* games — in the open arm a 2-backer wrong sign terminates the game, but under gating that game is demoted and keeps playing, and can win. The per-game log query split the 6 gated wins 1 re-routed : 5 resurrected (2 of them open-arm lies converted to wins), against 4 of the open arm's 5 correct 2-backer wins lost to re-route attrition. Net +1.

## Configuration

| lever | setting | ground |
|---|---|---|
| endgame | improved, always-on | `TargetMatch` (`Adjudication.classify:92`), `ModelTruthOracle` (Sonnet), commit nudge in `AgentActor` prompts + `GameView.render` clock (`LogActor.scala:346-348`) |
| arms | both, one invocation | `corroborationSigns ∈ {true, false}`; the open arm reproduces Run A's condition stamped and measured for the first time (its seam state was previously a config-default deduction) |
| oracle | `ErrorModel.perfect` | not `"systematic"`@1.0 — `blindSpot` uses `>= p` and flips at the boundary value, a nonzero fail-open that would break the deduction and read as a false outcome 3 |
| budget | `maxRounds = 16` | matches Run A (`RunWinRate`); a shorter budget confounds the win comparison, since gating defers signs toward end-of-budget |
| targets | dev-8 at n = 4; held-out-10 at n = 2 | `RunWinRate.targetNames` and `RunEndgameAudit.heldOut`; tagged `difficulty = "dev"` / `"held"` so `renderPrimary` reports them separately |
| society | Haiku agents, Sonnet truth oracle, seed-logged | live arm conventions unchanged |

52 games per arm, 104 total, roughly two hours of wall-clock.

## Protocol

1. Build `RunComposedCell` reusing `OracleSweep.sweep` plus the endgame audit's exact/loose print. Extend `renderPrimary` to split both fail-open and sign-correct **by sign path** (`BackerQuorum` vs oracle-confirmed); outcome 2's adjudication depends on the split.
2. Compute and print the **config-surface stamp** in the header: a hash over the three cohort system prompts (iterated from `AgentStrategy.cohort`), the oracle prompt template with the target placeholder factored out (widen `ModelTruthOracle.systemPrompt` visibility), the commit-nudge text in `GameView.render`, `maxRounds`, `k`, both model ids, and `TargetMatch`'s synonym table. Any element left out of the hash is declared in the header rather than implied comparable.
3. Run both arms on identical targets, N, and seeds discipline. Archive the **full event log of every game in both arms**, not just the `GameRecord`s — outcome 3 is adjudicated from logs, and a single instance suffices there.
4. Write the result into `fallible-oracle-results.md` as a measured fourth cell: the fail-open contrast within-run and de-stitched; the win result reported to the precision the N supports.

## Pre-registration — fixed before the run

1. **Fail-open headline** = pooled gated rate across 52 games. Success = the Wilson interval excludes 0.22 (the seam-open coupling rate it replaces). Expected 0/52 → [0.000, 0.069]. The dev-8 arm (N = 32) carries the magnitude claim; held-out alone (N = 20) certifies only zero-vs-nonzero.
2. **Wins** are reported as nonzero-vs-zero at N = 52; the magnitude does not resolve (4/52 → Wilson [0.030, 0.182] overlaps both a held 0.17 and a collapsed 0.05). **Inconclusive band: 1–5 pooled gated wins triggers a top-up to N ≥ 150**, the N at which the band separates and a ~2%-incidence third path reaches ~95% surfacing power (vs ~65% at 52).
3. **Run A `BackerQuorum` pre-pull, before the run:** count Run A's correct signs that came through the corroboration path, from its existing `GameRecord.signPath` fields. This number is the stated prior for outcome 2a's benign-loss magnitude; pulling it after the result would invite motivated reading.
4. Dev and held-out are reported separately; per-target n of 2–4 supports set-level statements only.
5. The seam-open arm is the control: it should reproduce Run A's fail-open (0.22–0.30) and win rate (0.15–0.19) under the stamp. If it does not, version drift is in play and the contrast is read with that caveat stated.

## Outcomes and adjudication

1. **Gated fail-open ~0, wins hold (nonzero, above the band).** The coupling was a seam-open artifact. The result is self-contained — wins > 0 and fail-open ~0 in one stamped run demonstrates "wins sometimes, never lies" at perfect reliability without reference to Run A. Headline reorganizes: trust is solved at this corner by construction and measurement; capability under degradation is the open problem, and it is a reasoner question.
2. **Gated fail-open ~0, wins drop (zero or below the open arm).** Split correct signs by path. (a) If the loss matches the pre-pulled count of Run A's correct `BackerQuorum` signs, it is the expected, benign cost of removing the standalone path — quantified, not alarming. (b) If the loss exceeds it, or gated logs show winners reaching the oracle and failing to sign, a gate/nudge interaction bug lives in the re-pose loop; the logs adjudicate.
3. **Gated fail-open above ~0.** A third sign path exists that the enumeration missed; at p = 1.0 a single instance is an existence proof, adjudicated from the archived log. This finding outranks everything in the queue, harness included. The two-path enumeration rests on a four-reader seam map, asserted not proven, so this outcome is live.

## Caveats carried

- **Metric relativity.** Fail-open certifies soundness relative to `TargetMatch`'s synonym table: guess-truth and grading share the matcher, so an over-broad synonym group could confirm-and-grade a wrong guess invisibly. No collision exists in the current vocabulary; the table is in the stamp, so any change visibly breaks comparability instead of silently redefining the metric.
- **Corner scope.** Every-route-shut holds only at p = 1.0. Gating `C` leaves `O`, and `O` is the entire fail-open under degradation. This cell certifies the perfect-oracle corner; degradation stays open by design.
- **Committee correlation.** The four reconciling lenses are one model family; their convergence could share a misreading. The one divergence (the `>= p` boundary catch) demonstrated independence; single-lens findings were verified against source, not settled by vote.

## Acceptance

- `RunComposedCell` compiles under the repo's `-Werror`/Scalazzi bar; `sbt check` green.
- Both arms run at the configuration above; the header prints the stamp and declares any unhashed element; per-set and per-path splits print for fail-open and sign-correct.
- Pre-registration items 1–5 were fixed before the run (this document, committed, is the evidence), the Run A pre-pull number is recorded in it, and the result lands in `fallible-oracle-results.md` as the fourth cell.

## Result — recorded 2026-07-09

`RunComposedCell`, live, stamp `e4666df175ff43e3` (society `claude-haiku-4-5-20251001`, truth `claude-sonnet-5`), exit 0, ~24 min, both arms' full logs archived.

- **Pre-pull (committed before the gated arm was read):** seam-open correct = 5, **all via BackerQuorum** (`co:bk = 5, co:or = 0`) → outcome-2a benign-drop ceiling = 5.
- **Seam-open control** reproduced the coupling: fail-open 0.25 (dev) / 0.35 (held), **all 15 via 2-backer**, `via-oracle = 0`; wins 0.16 / 0.00.
- **Seam-gated (the cell):** **fail-open 0/52 = 0.000 [0.000–0.069]** (excludes 0.22); sign-correct **6/52**, all via the **oracle** path; held-out wins exact-graded (2 = 2).
- **Verdict: OUTCOME 1.** Fail-open ~0, wins held (6 vs 5, drop −1 ≤ ceiling 5, above the 1–5 band → no top-up). The coupling was a seam-open artifact and was misattributed: the confident-wrongs were the open 2-backer seam, not the commit nudge. No outcome-3 fail-open; the two-path enumeration survives. Written into `fallible-oracle-results.md` as the fourth cell.

*End — 2026-07-09.*
