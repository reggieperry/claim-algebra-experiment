# The fallible-oracle experiment — results

**The measured deliverable of the design in `fallible-oracle-experiment-design.md`.** 2026-07-08. The design registered the analysis plan and the predicted shapes; this records what running it produced. Reproduce with the `RunOracleSweep` entry points named per section; the bench is deterministic where hermetic and seed-logged where live.

## Summary

- **The architecture is broadly graceful.** As the checker degrades, the loss goes overwhelmingly to abstention (0.67–1.00 across the sweep), not to wrong signatures. The gate declines rather than lies.
- **The fail-open has two distinct sources.** The sign-path split separates them cleanly: at a perfect oracle every fail-open is a **2-backer** sign (two agents agreeing on a wrong candidate, which the oracle never checks); at a degraded oracle every fail-open is an **oracle-confirmed** sign (a corrupted guess-confirmation). The first is the monoculture at the *agent* level; the second is what the confirmation quorum targets.
- **Redundancy pays for independence, not quantity.** The (k, ρ) correlation curve shows the oracle-confirmed fail-open falling as (1−p)^k when confirmations are independent and staying flat at (1−p) when they are correlated — the monoculture at the *confirmation* level.
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

The design's triage question is where to spend a fixed verification budget: on search (property questions, which lower the chance a wrong guess is posed) or on confirmation (a higher quorum, which lowers the chance a wrong guess is confirmed). The measured results settle its qualitative answer without a separate run, so it is reported as analysis rather than a third live sweep.

- Most of the fail-open mass is on the **2-backer path**, which no amount of guess-confirmation budget reaches — that path never consults the oracle. Verification spent on the final guess cannot touch the dominant fail-open source at a good-but-imperfect oracle.
- The part it *can* reach — the oracle-confirmed fail-open — barely benefits from more confirmations once the confirmations are **correlated**, and N Claude-based confirmations are correlated by construction (the ρ curve).

Both point the same way: a fixed verification budget is better spent on an **independent** check of the corroboration — a different model family, a mechanical oracle, a human on the two-backer agreement — than on re-confirming the same final guess with the same kind of checker. A fully-measured Arm 3 (fail-open per unit of verification spend, naive versus triage) is the remaining live effort; its direction is already fixed by the two results above.

## Threats to validity

- **Small N, wide intervals.** The live arm runs 12 games per cell; the intervals overlap, so no p-slope is claimed. The sign-path structure, not the point estimates, is the result.
- **The society is Claude-based.** Sign-correct is 0 across the sweep: the Haiku society rarely narrows to the true target for these hard targets, so the low win rate and high abstention partly reflect the model's difficulty with the task, not only the architecture. The architecture-level findings — abstention dominance, and the 2-backer versus oracle-confirmed split — do not depend on that difficulty.
- **The correlation ρ is dialed, not measured.** The bench measures the consequence of a given confirmation correlation, never the correlation real checks would exhibit.
- **A parametric oracle is not a real test suite.** The defense is isolation, not realism: the bench varies one thing — checker reliability — that a real system confounds with everything else.
