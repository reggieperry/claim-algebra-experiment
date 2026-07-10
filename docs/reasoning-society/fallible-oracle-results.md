# The fallible-oracle experiment — results

**The measured deliverable of the design in `fallible-oracle-experiment-design.md`.** 2026-07-08. The design registered the analysis plan and the predicted shapes; this records what running it produced. Reproduce with the `RunOracleSweep` entry points named per section; the bench is deterministic where hermetic and seed-logged where live.

## Summary

- **The architecture is broadly graceful.** As the checker degrades, the loss goes overwhelmingly to abstention (0.67–1.00 across the sweep), not to wrong signatures. The gate declines rather than lies.
- **The fail-open has two distinct sources.** The sign-path split separates them cleanly: at a perfect oracle every fail-open is a **2-backer** sign (two agents agreeing on a wrong candidate, which the oracle never checks); at a degraded oracle every fail-open is an **oracle-confirmed** sign (a corrupted guess-confirmation). The first is the monoculture at the *agent* level; the second is what the confirmation quorum targets.
- **Redundancy pays for independence, not quantity.** The (k, ρ) correlation curve shows the oracle-confirmed fail-open falling as (1−p)^k when confirmations are independent and staying flat at (1−p) when they are correlated — the monoculture at the *confirmation* level.
- **The gate is only as strong as its weakest sign path.** The accept rule's last requirement is a disjunction — two-agent corroboration *or* oracle confirmation — and it fires through the weak generative branch (corroboration) even where the oracle would have caught the error. The unit of trust is the sign path, not the gate. (Interpretation and next experiments: `fallible-oracle-interpretation-and-next-experiments.md`.)
- **The weak path can be closed, and the check that closes it must be non-generative.** Gating the corroboration branch with a ground-truth check drops the perfect-oracle fail-open from 0.25 to 0.00 without losing correct signs (E2), and that check has to be non-generative, because even different model families share blind spots on the hard cases (E3: same-model error correlation ρ≈0.75, cross-family ≈0.51, mechanical → 0).
- **The endgame's wins and confident-wrongs are separable, not coupled — the coupling was a seam-open artifact.** Correcting the grader and the oracle and making the society commit moved sign-correct off zero, but seam-open that came *with* a 0.22–0.30 fail-open, read at the time as the nudge trading abstention for confident-wrongs. The composed cell (improved endgame × seam-**gated** × perfect oracle, on the improvement's own targets) refutes the coupling: the fail-open was entirely the standalone 2-backer path, and gating it drops the fail-open to **0/52 [0.000–0.069]** while the wins **hold and slightly rise** (6/52 gated vs 5/52 open). The confident-wrongs were the open seam, not the nudge. And **fail-closed is productive, not merely safe**: closing the lying path *resurrects* games that would have terminated in a 2-backer sign — the per-game logs show 5 of the 6 gated wins are such resurrected games, two of them open-arm lies converted to wins — so it recycles would-be-lies into fresh attempts, enough of which win to offset the re-route attrition. What remains open is capability *under degradation* — the win rate still collapses at p < 1.0 — which the stronger-closer trial then tested directly (next bullet).
- **A stronger reasoner does not unblock the degraded regime — the bottleneck is the checker, not the tier.** The stronger-closer trial put Sonnet and then Opus in the closing+adversary roles (and, diagnostically, the searcher) at p = 0.7; neither unblocked correct signing above the all-Haiku baseline (S1 = 0/64 at both tiers; the lone Opus win 1/64 is within noise). Capability under degradation is **not** a model-tier question at these roles: a stronger reasoner reasons over the same corrupted premises and still needs the same corrupted confirmation to sign, so it cannot convert its strength into a correct signature. The founding thesis reaches the reasoner axis — past a point, a better reasoner does not buy soundness against a bad checker. (Refined by the reveal-the-set cell below: the *binding* constraint at p = 0.7 is the decoder's open search — revealing the target set unblocks to 8% — while the channel term is tier-invariant; both bind jointly, and removing either moves the win rate off zero.)
- **The gate fails closed on gaps, path-locally.** A genuine "don't know" on the question a conclusion depends on forces abstention; a gap off that path does not block. The first running-system exercise of Theorem 6.7.
- **The transferable lever is check diversity.** Both monocultures — agents that share blind spots, and confirmations that share a checker — say the same thing: stacking correlated checks does not buy the geometric safety the redundancy intuition promises.

## Arm 1 — the primary curves (live)

`RUN_PRIMARY_SWEEP` · Haiku society, Sonnet ground truth, systematic error model, targets {apple, dog}, k=1, maxRounds=12, N=12/cell (6/target). Bounded run — small N, wide intervals — sufficient to place the architecture on the H_graceful ↔ H_catastrophic axis, not to draw tight curves.

| p | N | fail-open (95% CI) | via 2-backer | via oracle | abstain | sign-correct |
|---|---|---|---|---|---|---|
| 1.00 | 12 | 0.17 [0.05–0.45] | 2 | 0 | 0.83 [0.55–0.95] | 0.00 |
| 0.70 | 12 | 0.00 [0.00–0.24] | 0 | 0 | 1.00 [0.76–1.00] | 0.00 |
| 0.50 | 12 | 0.33 [0.14–0.61] | 0 | 4 | 0.67 [0.39–0.86] | 0.00 |

**Reading.** Abstention is the dominant outcome at every reliability — the architecture leans graceful. The intervals overlap, so the p-trend is not statistically resolved at this N; the interpretable result is the **sign-path structure**, not the slope. At a perfect oracle (p=1.0) the fail-open is entirely 2-backer: a wrong guess at p=1.0 is answered `No` and masked, so an oracle-confirmed wrong sign is impossible there — the floor is two agents agreeing on a wrong candidate through the corroboration path, which never passes the oracle. At a degraded oracle (p=0.5) the fail-open flips to entirely oracle-confirmed: corrupted guess-confirmations, the crystal_vase mechanism. The two sources dominate different regimes.

**The agent-level monoculture.** The 2-backer floor is the redundancy intuition failing at the level of the agents themselves: two Haiku instances share blind spots, so a corroboration floor that trusts "two independent backers" is trusting two correlated ones. This is the same failure the confirmation-correlation curve measures, one layer up, and it is invisible to the oracle because the corroboration sign path does not consult it.

## The redundancy / correlation curve (hermetic)

`RunOracleSweep` default · hermetic, deterministic, p=0.7, N=800/cell, through the real gate and re-pose loop. Every sign here is an oracle-confirmed fail-open by construction (lone wrong guess), so the rate is the joint probability that all k confirmations corrupt.

| k | ρ=0 | ρ=0.5 | ρ=1 | (1−p)^k | (1−p) |
|---|---|---|---|---|---|
| 1 | 0.32 | 0.32 | 0.32 | 0.300 | 0.300 |
| 2 | 0.09 | 0.16 | 0.32 | 0.090 | 0.300 |
| 3 | 0.02 | 0.11 | 0.32 | 0.027 | 0.300 |

Along ρ=0 the fail-open falls geometrically with k; along ρ=1 it is flat regardless of k. ρ is an assumed, swept parameter — the curve makes the monoculture failure mode concrete as a function of a given correlation, and validates that the k-quorum machinery yields that rate end to end; it does not measure the real correlation of redundant model checks.

## Arm 2 — the partial oracle (Theorem 6.7)

`PartialOracleSuite` · hermetic, deterministic. A gap-injecting oracle returns a genuine `Unknown` on a chosen question class. Three correctness assertions, all green:

- **On-path gap forces abstention.** A gap on the guess-confirmation (the question the sign depends on) yields `Abstain`, never a signature — even when the guessed candidate is in fact the truth. Fail-closed even where signing would have been right.
- **Off-path gap does not block.** A gap on a property answer (belief-inert) does not prevent the sign; the society routes around it and signs.
- **The gate mechanics underneath.** An `Unknown` guess neither confirms (it is not a `Yes`) nor masks (it is not a `No`), so the candidate stays a lone unconfirmed winner and the pose budget is spent — termination, then abstention.

This is the first exercise of 6.7's gap-only, path-local fail-closed propagation in a running system rather than by construction: a gap blocks the sign exactly when it lands on the question the sign depends on, and nowhere else.

## Arm 3 — verification economics

The design's triage question is where to spend a fixed verification budget: on search (property questions, which lower the chance a wrong guess is posed) or on confirmation (a higher quorum, which lowers the chance a wrong guess is confirmed). The measured results point its direction, not its magnitude, so it is reported as analysis; its quantitative form is left to a further experiment (the good-but-imperfect sweep, E1 in the interpretation doc).

- One sign path is beyond any guess-confirmation budget: the **2-backer path** never consults the oracle, so spending there cannot touch it. What *share* of the fail-open lives on that path this run does not establish — the sampled cells put every fail-open cleanly on one path or the other (all 2-backer at p=1.0, all oracle-confirmed at p=0.5), and the good-but-imperfect band where the two compete was never sampled. The totals were 2 fail-opens via 2-backer against 4 via oracle-confirmed: the 2-backer path is established to *exist and be unchecked*, not established to *dominate*.
- The part a budget *can* reach — the oracle-confirmed fail-open — barely benefits from more confirmations once the confirmations are **correlated**, and N Claude-based confirmations are correlated by construction (the ρ curve).

The direction follows from the seam: a fixed budget is better spent on an **independent** check of the corroboration path — a different model family, a mechanical oracle, a human on the two-backer agreement — than on re-confirming the same guess with the same kind of checker. Pricing it precisely, and a fully-measured two-policy tradeoff (fail-open per unit of verification spend), remain live effort.

## E2 — closing the seam (the fix, and its measure)

The seam is `verify = C ∨ O`: the two-agent corroboration branch C signs without ground truth, so it fails open even at a perfect oracle. E2 closes it with a config flag, `corroborationSigns = false`, that drops the standalone C disjunct — a 2-backer candidate is demoted to an unconfirmed winner and must obtain a ground-truth guess-confirmation to sign, so `verify` narrows toward O alone. Byte-identical at the default; only the experiment sets the flag; a floor is never lowered, a check is added. Committee-approved and adversarially verified.

`RunSeamClosure` · live A/B at a perfect oracle (p=1.0, N=12/arm), fail-open split by sign path.

| arm | fail-open (95% CI) | via 2-backer | via oracle | abstain | sign-correct |
|---|---|---|---|---|---|
| seam-open (verify = C ∨ O) | 0.25 [.09–.53] | 3 | 0 | 0.67 | 0.08 |
| seam-gated (verify → O) | 0.00 [.00–.24] | 0 | 0 | 0.92 | 0.08 |

Closing the seam drops the perfect-oracle fail-open from 0.25 to 0.00: the three wrong 2-backer signs become abstentions — fail-closed, the recoverable outcome. The correct sign survives (0.08 in both arms), which is the decisive control: if the demoted candidates were silently dropped rather than posed to the oracle, the correct sign would have vanished too. It did not, so the candidates reach ground truth — the wrong ones answered No and struck, the right one confirmed and signed. `SeamClosureSuite` proves the same deterministically, plus the structural invariant that under seam-gated every signature is preceded by a Yes confirmation of it (no unchecked sign).

The fix and E3 meet exactly. Gating C with an independent check reduces, in the algebra, to routing C through O (`(C ∧ O) ∨ O = O`) because only ground truth knows the target — there is no realizable third mechanism. And that ground-truth check is non-generative, the one confirmer E3 measured to have joint error zero. The seam closes with the only genuinely independent check the study found.

## E0 — the endgame diagnostic (why sign-correct is zero)

`RunEndgameDiagnostic` · live, p=1.0 (perfect oracle), easy targets {dog, water, chair}, round budgets {12, 30}, N=12/budget. Separates "appropriately cautious" from "broken endgame" by reading the logs.

| round budget | posed a guess | posed the CORRECT guess | sign-correct | abstain | fail-open |
|---|---|---|---|---|---|
| 12 | 0.25 (3/12) | 0.00 (0/12) | 0.00 | 0.92 | 0.08 |
| 30 | 0.25 (3/12) | 0.00 (0/12) | 0.00 | 0.83 | 0.17 |

The zero win rate is **not a starved budget**: loosening the round budget from 12 to 30 left both the guess rate (0.25) and the correct-guess rate (0.00) unchanged. Even on easy targets at a perfect oracle, the society rarely reaches a lone candidate to guess, and when it does the candidate is wrong. The endgame and search are structurally weak at Haiku tier, independent of budget. Consequence: the good-but-imperfect sweep (E1) is **blocked** — a system that never wins has no working behavior whose degradation could be measured, so E1 is deferred behind an endgame fix rather than run. (The perfect-oracle fail-opens here, 1–2 of 12, are again 2-backer, consistent with the primary sweep.)

## E3 — the real error-correlation between confirmers

`RunConfirmerCorrelation` · live, 24 pre-registered hard yes/no questions (numerical/comparative and obscure-taxonomy items where capable models actually err) posed to three confirmers. The joint error rate is the fail-open rate under a two-confirmation requirement; ρ is the error correlation.

| pairing | err_A | err_B | joint (both wrong) | independent q_A·q_B | ρ |
|---|---|---|---|---|---|
| same model (Haiku × Haiku) | 0.21 | 0.21 | 0.167 | 0.043 | 0.75 |
| different family (Haiku × GPT) | 0.21 | 0.33 | 0.167 | 0.069 | 0.51 |
| model × mechanical | 0.21 | 0.00 | 0.000 | 0.000 | — |

An earlier run on common-misconception questions (tomato, whale, spider) returned zero errors for all three confirmers — capable models have learned those — so it could not measure ρ; the harder set above produces a measurable error rate.

The measured ρ turns the redundancy curve's dialed correlation into a number. Two instances of the same model err together far more than independence predicts (ρ ≈ 0.75; joint error 0.167 against an independent 0.043), so a second same-model confirmation buys almost nothing. A different model family lowers the correlation (ρ ≈ 0.51) but does not remove it — Haiku and GPT still share blind spots on the hard items, so cross-family diversity buys some but not all of the geometric benefit. Only the non-generative check drives the joint error to zero: the mechanical confirmer never errs, so requiring it catches every model error.

The deeper reading, and the one that vindicates the project's founding thesis: **diversity of model is not diversity of error.** Even different capable model families are correlated on the hard cases where a check matters; the only genuinely independent check is a non-generative one — a test, a proof, a lookup, a human. (N=24, so the ρ estimates are noisy; the ordering same-model > cross-family > mechanical, and the magnitudes, are the result, not the second decimal.)

## The endgame improvement — measured, not "fixed"

E0 found sign-correct = 0 and read it as structural. Live transcripts showed the society is a competent player mis-served by the apparatus, not a broken reasoner: it converged on "domestic dog" for target "dog" and the exact-string grader scored it wrong; the truth oracle returned UNKNOWN to answerable category questions ("is a dog a living organism?"), starving the search; the agents could not see the round budget, so never committed; and an agent asserted a compound "domestic dog or cat" that the skeptic refuted into a persistent glut.

Three corrections followed — semantic grading (`TargetMatch`: drop a leading qualifier, a pre-registered synonym set; grading only, hypothesis identity unchanged), a decisive truth oracle (answer category questions yes/no about a representative instance), and round-budget awareness with an endgame commit nudge. The result, stated plainly:

| target set | correct | wrong (fail-open) | abstain |
|---|---|---|---|
| dev-overlapping (8 targets, N=4, p=1.0) | 0.19 | 0.22 | 0.59 |
| held-out (10 unseen targets, N=2, p=1.0) | 0.15 | 0.30 | 0.55 |

Sign-correct moved off zero (0 → ~0.15–0.19). Two guards say this is not a grading rig: on the held-out targets exact-correct equals loose-correct (all three wins — hammer, kite, drum — signed the exact target word), so `TargetMatch` changed no held-out outcome; and an adversarial review found `TargetMatch` conservative (it fails to credit "green apple", "guide dog", "notebook" and never credits a wrong thing) with no sealed-target leak.

But this is an improvement, not a fix. When the society commits it signs wrong about as often as right (held-out 3 correct to 6 wrong; fail-open 0.30), and at a degraded oracle the win rate collapses to zero (below). The wins and the confident-wrongs looked **coupled through the commit nudge**: pushing a weak model to commit appeared to trade safe abstention for more confidently-wrong signatures. **This attribution is corrected below (the composed cell):** the fail-open measured here was seam-OPEN, and all of it is the standalone 2-backer path — gating the seam zeroes it while the nudge-driven wins survive through the oracle, so the confident-wrongs were the open seam, not the nudge. A proper fix for the win rate *under degradation* still needs a stronger reasoner, which the weak-model-under-test design excludes.

> **Deferred decision — the commit nudge.** The round-budget nudge is what produces guesses at all, and also what manufactures the confident-wrongs; whether to dial it back (trading win rate for a lower fail-open) is left open. Reproducibility caveat: the nudge is unconditional, so the post-fix society asserts more than the pre-fix baseline, and prompts are not versioned in the trial record — pre- and post-fix numbers are not directly comparable.

## The composed cell — the coupling was a seam-open artifact (2026-07-09)

The coupling claim above rested on an inference across two runs with different conditions: the endgame-improvement run measured the improved society **seam-open** (wins 0.15–0.19 *with* fail-open 0.22–0.30), while E2's seam closure measured a **different, un-improved** system on different targets. The composed cell runs the one corner never measured — improved endgame × seam-gated × perfect oracle — on the improvement's own target sets (dev-8 N=4, held-out-10 N=2), with the seam-open arm rerun in the same invocation as a within-run control. Pre-registered in `fallible-oracle-composed-cell-experiment.md`; `RunComposedCell`, live (Haiku society, Sonnet truth), config-surface stamp `e4666df175ff43e3`, both arms' full event logs archived per game.

| arm | fail-open (95% CI) | fo:bk | fo:or | abstain | correct | co:bk | co:or |
|---|---|---|---|---|---|---|---|
| seam-open, dev-8 (N=32) | 0.25 [.13–.42] | 8 | 0 | 0.59 | 0.16 | 5 | 0 |
| seam-open, held-out (N=20) | 0.35 [.18–.57] | 7 | 0 | 0.65 | 0.00 | 0 | 0 |
| **seam-gated, dev-8 (N=32)** | **0.00 [.00–.11]** | 0 | 0 | 0.88 | 0.13 | 0 | 4 |
| **seam-gated, held-out (N=20)** | **0.00 [.00–.16]** | 0 | 0 | 0.90 | 0.10 | 0 | 2 |

**Fail-open — the deductive headline, confirmed:** pooled seam-gated **0/52 = 0.000 [0.000–0.069]**, excluding the 0.22 it replaces. The seam-open arm reproduces the coupling (fail-open 0.25 / 0.35), and every one of its 15 fail-opens is a 2-backer sign (`fo:or = 0`) — so at a perfect oracle the entire fail-open is the corroboration path, and gating it zeroes the fail-open. The control reproduced Run A's fail-open on both sets; on the win side its held-out arm came in at 0.00 against Run A's 0.15 (0 of 20 vs 3 of 20 — compatible with sampling noise at this N). Per pre-registration item 5 that win-side miss carries a version-drift caveat against Run A, while the within-run contrast the cell was built around is unaffected.

**Wins — the open question, answered:** seam-gated sign-correct **6/52 vs seam-open 5/52 — a rise of one, not a loss.** This refutes the pre-registered structural expectation that gated wins would be ≤ open wins (annotated as superseded in the spec): the ≤ argument counted re-routed winners but not *resurrected* games. The benign-drop ceiling (`co:bk = 5`, the within-run open arm's 2-backer wins, committed before the gated arm was read) is moot — there was no loss to bound. (That ceiling is a declared deviation from pre-registration item 3, which registered Run A's *own* `BackerQuorum` count: Run A's records were never persisted — verified — so the within-run open arm is both the only available source and the sounder one, same-stamp.) Every gated correct sign is via the **oracle** path (`co:or = 6, co:bk = 0`); the per-game log query below shows *how* the wins survived, and it is not the re-routing the ≤ argument assumed. The two held-out wins are exact identifications (loose = exact = 2), and wins held above the 1–5 inconclusive band, so no top-up was triggered.

**Re-routed vs resurrected — the per-game log query.** Matching each of the 6 gated wins to its same-`(target, seed)` open-arm game (the arms share per-game seeds, so filenames match across arms) splits the wins **1 re-routed : 5 resurrected**. Only one gated win — a dev-8 `dog` game — also won in the open arm, its 2-backer winner re-routing to the oracle. The other five are *resurrected*: in the open arm they abstained (3) or **fail-opened via 2-backer (2)** and terminated there; gating demoted the premature 2-backer sign, the game kept playing, and it won. Two gated wins are therefore **a lie converted to a win** — `dev spoon` and `held-out pencil`, each an open-arm 2-backer fail-open on the same target that becomes a correct signature under gating. The held-out arm is the existence proof re-routing could not give: the open arm had zero held-out wins, so its two gated wins cannot be re-routed. The inverse prices the mechanism the ≤ argument was actually about — of the open arm's 5 correct 2-backer wins, only 1 survives gating; **4 are lost to re-route attrition** (they never reach an oracle confirmation within budget). So the net +1 is 5 resurrected minus 4 attrition: closing the lying path does not merely preserve the wins, it *reshuffles* them, recycling would-be-terminated games into fresh attempts — enough of which win to more than offset the winners that fail to re-confirm.

**Mechanism confirmed within-game (demotion-event query).** The 1:5 split is a paired inference — same-seed twins whose *oracle draws* match but whose live sampling does not — so a within-game check is needed to tell mechanism from variance. Reading each gated log for a demoted 2-backer quorum event (a candidate reaching ≥ 2 distinct backers while `corroborationSigns = false`, hence not signing) bins all nine games as **mechanism-confirmed**: every one of the five resurrected wins is preceded by a demoted quorum on the winning candidate that then oracle-confirmed and signed (`dev spoon` at event 40, `held-out pencil` at 60 and 74 — the two lie→win pairs, their gated wins traced to a demoted quorum and paired against open twins that signed a *wrong* 2-backer candidate at the same seed), and every one of the four attrition games reached a demoted quorum that then failed to oracle-confirm within budget and abstained (true re-route attrition, not variance). The resurrection/attrition accounting is therefore a traced mechanism, not a paired-outcome coincidence.

**So the coupling was a seam-open artifact, and it was misattributed.** The confident-wrongs were not manufactured by the commit nudge — they were the standalone 2-backer signs of the open seam, and every one is gone under gating; the nudge produces the *wins*, through the oracle, and those survive. Wins and confident-wrongs are separable, not coupled. The strongest sentence the data supports: **at perfect check reliability, seam-gating decouples the win rate from confidently-wrong signatures — the improved society wins ~0.12 and never lies, by the deduction and by measurement.** No outcome-3 fail-open appeared, so the two-path sign enumeration survives this cell. The remaining open problem is capability *under degradation* (the win-rate collapse at p < 1.0, next section) — a reasoner question, not an architecture one.

## The verification-spend tradeoff — no live signal

The intended experiment was to run the winning system at a degraded oracle and compare fail-open per unit of verification spend — more same-family confirmations versus an independent check. Seam-gated (so the confirmation is the sole sign path) at p = 0.7, under three policies — k=1, k=2 correlated at ρ=0.75 (redundancy, E3's same-family value), k=2 at ρ=0 (diversity) — it returned nothing to compare: every policy signed nothing (fail-open 0.00, correct 0.00, abstain 1.00 across 16 games each). The improved system does not win reliably enough at a degraded oracle to generate confirmed signs, so there is no live tradeoff to measure — the endgame weakness E0 found, only partly remedied.

The clean characterization of the tradeoff is therefore the hermetic correlation curve (fail-open vs k and ρ) read at E3's measured correlation: at ρ ≈ 0.75 (two same-family confirmations) redundancy buys almost nothing, while an independent check (ρ → 0) approaches the geometric floor (1−p)^k. Every input is measured — the correlation (E3), the reliability p — but the combination is the hermetic curve, not a live sweep, because the live system cannot supply the confirmations.

## The stronger-closer trial — capability under degradation is not gated by model tier (2026-07-09)

The composed cell named the open frontier as capability under degradation — "a reasoner question, not an architecture one" — and the verification-spend tradeoff stayed hermetic because the all-Haiku society signs nothing at p = 0.7. Both point to one test: does a stronger reasoner in the reasoning roles unblock signing at a degraded oracle? Pre-registered (`stronger-closer-trial-experiment.md`, committee-reconciled to the unblock question), `RunStrongerCloser`, live, two tiers (Sonnet, Opus) against a shared all-Haiku baseline (same surface stamp `09b93e3e54d0f1f1` across both), seam-gated, p = 0.7, k = 1, N = 64/arm, three arms per tier: W (all Haiku), S1 (strong closer + adversary, cheap splitter), D (strong everything — the closer-vs-search diagnostic).

| tier | arm | correct | fail-open | abstain |
|---|---|---|---|---|
| — | W (all Haiku) | 0/64 | 0.05 | 0.95 |
| Sonnet | S1 (strong closer+adversary) | 0/64 | 0.02 | 0.98 |
| Sonnet | D (strong everything) | 0/64 | 0.02 | 0.98 |
| Opus | S1 (strong closer+adversary) | 0/64 | 0.02 | 0.98 |
| Opus | D (strong everything) | 1/64 | 0.05 | 0.94 |

**Not unblocked at either tier.** Neither Sonnet nor Opus, in the closing+adversary roles (S1) or across all three (D), signs distinguishably more correctly than all-Haiku — every S1−W Newcombe interval includes 0, and the single Opus-D win (1/64) is within noise (D−S1 includes 0). The null reproduced (W drift rate 0.000, within the pre-committed band). **Outcome 4, the tier split, is refuted: Opus does not cross a threshold Sonnet missed; both are outcome 3.** The null is firm in the sense that S1 is *literally* 0 at both tiers, not a marginal effect a larger N would resolve into an unblock.

**So "capability under degradation is a reasoner question" is refuted for the tier ladder tested.** Allocating even frontier compute (Opus) to the closer, the adversary, and the searcher does not let the society win at a degraded oracle; it stays abstention-dominated (~95%). The defensible reading: the bottleneck under degradation is not the reasoner tier at these roles — it is the **checker**. A stronger reasoner reasons over the same corrupted premises and still needs the same corrupted confirmation to sign, so it cannot convert its strength into a correct signature. This is the experiment's founding thesis reaching the reasoner axis: past a point, a better reasoner does not buy soundness against a bad checker.

**The `w` gradient confirms the tradeoff-starvation the composed cell's committee flagged.** The wrong-guess-reached-confirmation base rate *falls* as the tier strengthens (Sonnet: W 0.141 → S1 0.063 → D 0.016; Opus S1 0.031) — a stronger closer makes wrong commits rarer, starving the wrong-sign signal the deferred verification-spend tradeoff would read. The tradeoff stays hermetic now for two independent reasons: the society signs too little at p < 1.0, and a strong closer suppresses what little wrong-sign signal remains. (Caveats: one p, the dev-8 targets, N = 64/arm, this allocation; this says the Haiku→Sonnet→Opus ladder does not move the degraded-oracle win rate off ~0 at these roles — not that no reasoner ever could.)

## The reveal-the-set cell — the p = 0.7 null was decoder-bound, not channel (2026-07-09)

The capacity pass (`capacity-estimate.md`) left the stronger-closer null as a *joint* channel+decoder ceiling and named the decisive separating test: reveal the target set to collapse the open everyday-noun search to the true 3-bit identification, and see whether the win rate moves. Pre-registered (`reveal-the-set-cell.md`), `RunRevealSet`, live, two all-Sonnet arms at p = 0.7, seam-gated, k = 1, N = 64/arm — A (set withheld, the current society) and B (the eight-word candidate set revealed to every agent as a trusted system-prompt preamble; the target stays sealed in the oracle).

| arm | correct | fail-open | abstain |
|---|---|---|---|
| A — set withheld (N = 64) | 0/64 | 0.02 | 0.98 |
| B — set revealed (N = 62) | **5/62 = 0.08** | 0.02 | 0.90 |

**Revealing the set unblocks.** B's correct-rate exceeds A's by a distinguishable margin — **Newcombe B − A = [0.008, 0.175], excluding 0** — while A reproduces the stronger-closer D-Sonnet null (0/64) exactly (same config, shared seeds). So the p = 0.7 null was **decoder-bound**: the society could not win because it was searching the open space, not because the channel forbade winning the collapsed task. All five B wins are oracle-confirmed, and the wrong-guess-reach rate `w` is unchanged (0.016 in both arms) — so the reveal helped the society find the *right* candidate, not brute-force more guesses.

**N = 62 accounting, and the thin margin.** B ran the same 64 games as A (8 targets × 8 seeds); two — `cup` and `tree`, one seed each — errored mid-run against the live API and produced no completed record, so B is scored over the 62 that finished (A completed all 64). The asymmetry does not carry the result: **any disposition of the two dropped games preserves the exclusion** — count both as non-wins and B is 5/64 with Newcombe B − A = [0.006, 0.170]; count both as wins and it is 7/64 with [0.030, 0.209]; both still clear zero. The effect is nonetheless thin — five wins, and the B − A lower bound sits at 0.008, a hair above zero — so the finding is **directional**: revealing the set moves the win rate off an *exact* zero (A is 0/64, not a rounded-down small number), which is the claim, but 0.08 is not a precise rate and a larger N would be needed to pin the magnitude. What the small N cannot erase is the sign, because the baseline it is measured against is a hard zero.

**This confirms and quantifies the capacity decomposition, and refines "the bottleneck is the checker."** The stronger-closer null (0 at p = 0.7, withheld) is a joint ceiling of the degraded channel and the open search, and removing *either* constraint unblocks: revealing the set (collapse the search) → 8% at p = 0.7; a perfect channel (the composed cell, withheld) → 12% at p = 1.0. A stronger *tier* does not help because every tier still searches the open space; revealing the *set* helps because it collapses that search. So the surviving derived claim holds — a stronger tier cannot manufacture channel bits (which is why W/S1/D nulled together) — but the *binding* constraint at p = 0.7 is the decoder's **open search**, a protocol property (the withheld set), not the channel. The channel is not irrelevant: B wins only 8%, far below the ~46% a naive single-guess capacity would allow for the 3-bit task, so the degraded channel and the society's imperfect decoding both still limit B. The decisive fact is that removing the open-search burden moves the win rate off zero — the open search was a binding constraint the tier ladder could not touch.

## Threats to validity

Each threat is scoped to the arm it describes; a measured section that invalidates one updates it here (the structural rule below).

- **Small N, wide intervals.** The *primary* live arm runs 12 games per cell; the composed cell runs 52 per arm (32 dev-8, 20 held-out); the hermetic correlation curve runs 800 per cell. Intervals overlap where N is small, so no p-slope is claimed from the primary sweep, and the composed cell's per-target n of 2–4 supports set-level statements only — no per-target inference.
- **The society is Claude-based.** In the *original* sweep sign-correct was 0 — the Haiku society rarely narrowed to the true target — so the low win rate there partly reflects the model's difficulty, not only the architecture. The endgame improvement moved it to ~0.15–0.19 and the composed cell to 6/52; the architecture-level findings (abstention dominance, the 2-backer versus oracle-confirmed split) do not depend on the win rate.
- **The correlation ρ was dialed in the hermetic curve, then measured in E3.** The curve demonstrates the *consequence* of a given confirmation correlation; the real ρ is estimated separately (E3: same-model ≈ 0.75, cross-family ≈ 0.51, mechanical → 0, N=24), and the curve's external validity now rests on E3.
- **The composed cell's fail-open is relative to `TargetMatch`'s synonym table.** Guess-truth and grading share the matcher, so an over-broad synonym group could confirm-and-grade a wrong guess invisibly; no collision exists in the current dev-8/held-out vocabulary, and the table is in the run's config-surface stamp, so a change breaks comparability visibly rather than silently redefining the metric.
- **The composed cell certifies only the p = 1.0 corner.** Gating C leaves O, and O is the entire fail-open under degradation; the every-route-shut result holds at a perfect oracle, and capability under degradation stays open by design.
- **The reveal cell — the dropped games.** B is scored over 62 games (`cup` and `tree`, one seed each, errored against the live API and produced no completed record); A completed all 64. The asymmetry does not carry the finding: any disposition of the two dropped games preserves the exclusion — both as non-wins gives 5/64, Newcombe B − A = [0.006, 0.170]; both as wins gives 7/64, [0.030, 0.209]; neither touches zero.
- **The reveal cell — single-cell scope.** The result is one cell — p = 0.7, all-Sonnet, k = 1, N = 64/arm — not a curve, and it was not replicated. It establishes the *sign* (revealing the set moves the win rate off an exact-zero baseline) but not a magnitude: the margin is thin (five wins, a lower bound of ~0.006–0.008), 0.08 is not a precise rate, and a frontier-p estimate would need N ≈ 150–200 per cell.
- **The reveal cell — the truthful-reveal assumption.** The reveal is a *trusted* system-prompt preamble that names the eight candidates truthfully; it is a strong intervention that both collapses the open search and reframes the task, so the cell isolates "decoder-bound versus channel-bound," not the finer question of how much of the collapse is recognition versus reframing. A mis-stated or adversarial reveal would confound it; the unchanged wrong-guess-reach rate (`w` = 0.016 in both arms) argues it collapses the search rather than inducing more guessing.
- **A parametric oracle is not a real test suite.** The defense is isolation, not realism: the bench varies one thing — checker reliability — that a real system confounds with everything else.

> **Structural rule (the librarian applied to this document).** The threats section is part of every revision's acceptance: each new measured section either updates the threat it invalidates or dates it to the arm it describes. A defeated threat left on the live list is a claim carried past its refutation — the same discipline the belief ledger enforces on the board.
