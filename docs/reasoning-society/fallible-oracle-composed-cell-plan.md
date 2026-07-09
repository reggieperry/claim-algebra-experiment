# Composed-cell build plan — improved endgame × seam-gated × perfect oracle

Status: committee-reconciled. Verdict PROCEED_WITH_CHANGES (four lenses, high confidence,
2026-07-09); the five blocking changes are folded in below and the reconciliation is recorded at the
end. One cell of the experimental grid that composes the two fixes which were only ever validated
separately.

## The gap

The endgame improvement — semantic grading (`TargetMatch`), a decisive truth oracle
(`ModelTruthOracle`, Sonnet), and round-budget awareness with a commit nudge — ran seam-open and
produced wins at 0.15–0.19 with fail-open at 0.22–0.30. That is the current headline: *the wins and
the confident-wrongs are coupled through the commit nudge.* E2's seam closure
(`corroborationSigns = false`) ran as its own A/B on a different target mix and produced fail-open
0.00 with wins preserved. Nobody has run the composition: improved endgame + `corroborationSigns =
false` + p = 1.0 + the improvement's own target sets.

The current headline conclusion rests on an inference across two runs with different conditions —
different endgame configuration (Run A improved, sign-correct 0.15–0.19; Run B un-improved,
sign-correct 0.08), different targets (Run A dev-8/held-out-10, Run B `{apple, dog}`), different N
(4/2 vs 12/arm). Commit lineage confirms the confound: Run B (`RunSeamClosure`) predates the
endgame-improvement commit that added `TargetMatch`, `ModelTruthOracle`, and `RunWinRate`. That is
precisely the kind of claim this program does not accept from itself.

## Why the cell is decisive — and where the deduction stops

At a perfect oracle, an oracle-confirmed wrong sign is deductively impossible — a wrong "Is it X?"
gets answered No and struck. With the seam gated, the 2-backer path is closed too. Two enumerated
sign paths exist (`verify = C ∨ O`, `GameCore.decide` 196–199): C is 2-backer corroboration
(`backers ≥ MinCorroboration = 2`), O is oracle confirmation (`oracleConfirmations ≥ k`).
Seam-gating drops C; a perfect oracle makes O incapable of confirming a wrong candidate. The algebra
is `(C ∧ O) ∨ O = O`, and `SeamClosureSuite` already proves that under seam-gated every signature is
preceded by a Yes confirmation of it. All four sign-emission sites in `LogActor` thread
`config.corroborationSigns`, so the gate leaks at no path.

So the **fail-open half of the prediction is deductive**: at this cell fail-open is structurally ~0,
bounded only by an unenumerated third sign path (outcome 3). The **win half is not a deduction, it
is the open question the cell exists to answer** — see the next two sections.

## The design: a within-run A/B, both arms stamped

Running only the gated arm and comparing its fail-open against the historical, unstamped Run A would
re-commit the cross-run inference — the same error the cell is meant to retire. So the cell runs
**both arms in one invocation** on identical targets, N, and budget, the way `RunSeamClosure`
already does on `{apple, dog}`, and stamps both. The seam-open arm reproduces Run A's coupling under
the exact stamped configuration; the seam-gated arm is the treatment. The fail-open contrast is then
within-run and same-version, not stitched.

| lever | how it is set | ground |
|---|---|---|
| improved endgame | always-on, no flag | `TargetMatch` in `Adjudication.classify:92` + `ExperimentOracle.guessTruth`; `ModelTruthOracle.systemPrompt` (Sonnet); commit nudge in `AgentActor` prompts + `GameView.render` endgame clock (the USER message, `LogActor.scala:346-348`) |
| **both arms** | one sweep, `corroborationSigns ∈ {true, false}` | reproduce `RunSeamClosure`'s within-run A/B on dev-8 + held-out-10 |
| seam gated (treatment) | `cfg.copy(corroborationSigns = false)` | `GameCore.decide` 196–199: drops the `C` disjunct, sign iff `oracleConfirmations ≥ k` |
| perfect oracle | `SweepCell(reliability = 1.0, "perfect", …)` | **`ErrorModel.perfect`** (`ErrorModel.scala:40`). NOT `"systematic"`@1.0 — `SystematicPerQuestion.blindSpot` uses `>= p`, so it flips at `h == Int.MaxValue`, a measure-zero but nonzero fail-open that would break the deduction. |
| budget pinned | `SocietyConfig(maxRounds = 16, …)` | match `RunWinRate` (16), not `RunSeamClosure` (12) — a shorter budget confounds the win A/B against Run A, since seam-gating defers signs to the end-of-budget give-up ladder |
| dev-8, N=4 | `RunWinRate.targetNames` | `{dog, apple, chair, spoon, book, tree, cup, shoe}`, `n = 4`; tag `SweepCell.difficulty = "dev"` |
| held-out-10, N=2 | `RunEndgameAudit.heldOut` | `{banana, pencil, clock, guitar, hammer, sock, kite, umbrella, candle, drum}`, `n = 2`; tag `difficulty = "held"` |

52 games per arm (8×4 + 10×2), 104 for the A/B, roughly two hours of Haiku wall-clock. Live — Haiku
society, Sonnet truth oracle, Anthropic API key only, seed-logged. A new `IOApp` `RunComposedCell`
reusing `OracleSweep.sweep` + the audit's exact/loose print. The two target sets carry **distinct
`SweepCell.difficulty` tags** so `renderPrimary` (which groups by cell) reports dev-8 and
held-out-10 separately rather than pooling them.

## The prediction: sharp on fail-open, open on wins

- **Fail-open (sharp, deductive):** seam-gated fail-open falls to ~0. The pooled seam-gated headline
  is 0/52 → Wilson [0.000–0.069], which cleanly excludes the 0.22–0.30 it replaces. The seam-open
  arm should reproduce Run A's 0.22–0.30, confirming the contrast is the seam and not the
  configuration. (The held-out arm alone at N=20 certifies only zero-vs-nonzero, not a magnitude
  reduction; the dev-8 arm at N=32 carries the magnitude.)
- **Wins (open, not predicted):** seam-gated wins are **structurally ≤ seam-open wins** — removing
  the 2-backer standalone sign path re-routes those winners to the oracle, and any that exhaust the
  budget before posing the winning guess legitimately abstain; the extra rounds also expose winners
  to rivals/refutes that can push them to Ambiguous. Whether the improved society's wins survive
  gating at p = 1.0 is the question, not the prediction. The on-point prior is encouraging but not
  settling: the p = 1.0 seam-gated arm already preserved correct wins at 0.08 on `{apple, dog}`
  (`results.md:70`) — direct evidence that gating at a perfect oracle does not zero wins — though
  that was the un-improved endgame, so it bounds without settling the improved rate. The p = 0.7
  `RunSpendTradeoff` (signed nothing) is a degraded-oracle confound, not the relevant anchor.

## Power and pre-registration

At pooled N = 52 the fail-open claim resolves but the win-magnitude claim does not: 4/52 → Wilson
[0.030–0.182] overlaps both a held 0.17 and a collapsed 0.05, so a partial win count cannot separate
outcome 1 from outcome 2. Pre-register, before the run:

- **Fail-open headline** = pooled seam-gated rate; success is an interval excluding 0.22.
- **Sign-correct** is verifiable only as **nonzero vs zero** at N = 52, not to ±0.05. An
  **inconclusive win band of roughly 1–5 pooled wins triggers a top-up to N ≥ 150** (the N at which
  the middle band separates and at which a ~2%-incidence third sign path reaches ~95% surfacing
  power; at N = 52 that power is only ~65%).
- Report dev-8 and held-out-10 separately; per-target N = 2/4 supports set-level reporting only, no
  per-target inference.

## The process fix: stamp the config surface, not just the prompts

From the results' own caveat: prompts are not versioned in the trial record, so pre- and post-fix
numbers are not directly comparable. `GameRecord` (`OracleSweep.scala:26`) records only
`{cell, trueTarget, signed, outcome, signPath, seed}`. Add a stable **config-surface stamp** carried
on the record and printed in the header — a hash over everything that changes comparability, not
prompts alone:

- the three cohort system prompts (iterate `AgentStrategy.cohort`; there is no static
  `AgentStrategy.systemPrompt` — it is a per-instance field);
- the oracle prompt **template** (`ModelTruthOracle.systemPrompt` is `private` and interpolates
  `target.value`, so widen visibility and hash the template with the target placeholder factored
  out — not a per-target render);
- the endgame commit-nudge text in `GameView.render` (improvement 3 lives in the USER message, not
  any system prompt);
- `maxRounds`, `k`, the model ids (Haiku society / Sonnet oracle), and `TargetMatch`'s synonym
  table.

Scope it as its own slice so the science cell and the process fix are separable. If any of these are
left out of the hash, say so in the header rather than implying full comparability.

## Three readable outcomes

1. **Fail-open ~0, wins hold.** The coupling dissolves; it was a seam-open artifact. This conclusion
   is self-contained — wins > 0 and fail-open ~0 in one stamped run demonstrates "wins sometimes,
   never lies" without reference to Run A. The honest headline reorganizes: at perfect check
   reliability the system wins sometimes and never lies, by construction and by measurement; the
   open problem is capability under degradation, a reasoner question, not an architecture question.
2. **Fail-open ~0, wins drop.** Two sub-cases, distinguished by splitting correct signs by sign
   path (a `renderPrimary` change): (a) the **expected, benign** loss of Run A's 2-backer standalone
   correct wins that do not reach the oracle in budget — quantify it by counting Run A's correct
   `BackerQuorum` signs; or (b) a genuine gate/nudge interaction bug in the re-pose loop — read the
   logs. Outcome 2 is at least as likely as outcome 1, and a drop is not by itself a bug.
3. **Fail-open above ~0.** A third sign path exists that neither E2 nor the deduction accounts for,
   the sign-path audit is incomplete, and that finding immediately outranks everything else in the
   queue, harness included. The two-path enumeration is asserted by a four-reader seam map, not
   proven exhaustive, so this outcome is live; at p = 1.0 a single instance is an existence proof.

## Caveats surfaced by grounding

- **Run A's seam-open state was never printed** — `RunWinRate` takes the `corroborationSigns = true`
  default; the docs deduced seam-open. The both-arms design makes the condition explicit and
  measured for the first time.
- **The fail-open metric certifies soundness only relative to `TargetMatch`'s synonym table**, not
  absolute semantic correctness: guess-truth (`matchesRaw`) and the grader (`matches`) share the
  matcher, so an over-broad synonym group could confirm-and-grade a semantically-wrong guess without
  it ever showing as fail-open. No collision exists in the current dev-8/held-out vocabulary — a
  forward guard, not a live bug.
- **Scope of the claim.** "Every route shut" holds only at p = 1.0. Shutting C leaves O, and O is
  the entire fail-open at p < 1.0. This cell certifies the perfect-oracle corner; degradation stays
  open by design.

## Acceptance

- `RunComposedCell` compiles under the repo's `-Werror`/Scalazzi bar and `sbt check` stays green.
- The cell runs both arms (seam-open, seam-gated) × dev-8 × held-out-10 at `ErrorModel.perfect`,
  `maxRounds = 16`, prints per-game and per-set fail-open (split by sign path), sign-correct (split
  by sign path), abstain, with the config-surface stamp in the header.
- The pre-registered fail-open headline and win band / top-up trigger are stated before the run.
- The result is written into `fallible-oracle-results.md` as a measured fourth cell — the fail-open
  contrast within-run and de-stitched; the win result reported to the precision the N supports.

---

## Committee reconciliation (2026-07-09)

Four independent lenses (design-validity, code-wiring, adversarial-sign-path, statistical-power),
each verifying against source; verdict **PROCEED_WITH_CHANGES**, high confidence, unanimous. The
cell is the right experiment and the fail-open headline is deductively sound at p = 1.0 seam-gated;
the changes below were folded in above.

Blocking changes applied: (1) `ErrorModel.perfect`, not `"systematic"`@1.0 — the latter flips at a
measure-zero boundary (`blindSpot` uses `>= p`), which would break the fail-open=0 deduction;
`maxRounds = 16` pinned. (2) Run both arms concurrently, one stamped invocation, so the fail-open
contrast is within-run rather than stitched against the unstamped Run A. (3) The win prediction
reframed as the open question — a drop is the expected cost of removing the 2-backer path, not a
bug; outcome 2 at least as likely as outcome 1. (4) Pre-registered inconclusive win band (1–5 pooled
wins → top-up to N ≥ 150); the fail-open headline resolves at N = 52, the win magnitude does not.
(5) Prompt-stamp identifiers corrected (per-instance cohort prompts; private, target-parameterized
oracle template) and the stamp widened to the full config surface.

Kept open by the committee: whether seam-gated wins hold or collapse is genuinely unresolved even
after the fixes; outcome 3's surfacing power is only ~65% at N = 52; whether the 0.08 un-improved
prior bounds the improved-endgame rate.

Correlation caveat: all four lenses are the same Opus model family and are not independent — their
convergence on the core code facts is reassuring but could reflect a shared misreading. One
divergence is worth trusting: only the adversarial lens caught the `>= p` boundary, which is why
`ErrorModel.perfect` became blocking. Single-lens findings (the `renderPrimary` pooling, the
`GameView`-clock stamp gap) are leads verified against source, not settled by vote.
