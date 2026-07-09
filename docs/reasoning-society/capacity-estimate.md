# The capacity estimate — committee-reconciled: REFUTED as a pure ceiling, kept as a decomposition

**Committee pass complete (four lenses, 2026-07-09). The headline upgrade — "at p = 0.7 the stronger-closer null is a capacity ceiling for ANY reasoner" — is REFUTED. The arithmetic, the binary-symmetric-channel reading, and the adaptivity resolution all stand (every lens reproduced them), but the estimate conflated the *decoder's* open search-space entropy with the *channel's* bound. What survives, and is more precise than the original, is a decomposition: the null has two independently-binding causes — a channel term that is reasoner-independent, and a decoder/search term that is tier- and protocol-dependent and is the sole cause at p = 1.0.** 2026-07-09.

---

## The sound core (verified)

At a degraded oracle p, each yes/no answer is a binary symmetric channel with crossover 1 − p; capacity **C(p) = 1 − H₂(1 − p)**. Reproduced by every lens to stated precision:

| p | C(p) bits/answer | 16 rounds → bits |
|---|---|---|
| 0.70 | 0.119 | 1.90 |
| 0.80 | 0.278 | 4.45 |
| 0.85 | 0.390 | 6.24 |
| 0.90 | 0.531 | 8.50 |
| 0.95 | 0.714 | 11.42 |
| 1.00 | 1.000 | 16.0 |

Confirmed against source: a property answer is flipped with probability exactly 1 − p (`ErrorModel.IndependentUniform`/`CorrelatedConfirmations` at ρ = 0 is i.i.d. BSC; `SystematicPerQuestion` makes a *deterministic* fraction wrong, so only *distinct* questions are ~independent draws — re-asking averages one source bit, a capacity-bounded repetition code). Guess-confirmations are additional C(p) draws through the same corruption. **Adaptivity does not beat the bound**: for a memoryless channel, feedback achieves but cannot exceed capacity (Shannon 1956), so a stronger, adaptive questioner cannot manufacture channel bits — this closes the estimate's own "maybe the adaptive protocol beats it" escape hatch *in the null's favour*. And 16·C(p) is a **generous upper bound** on the real evidence budget (no semantic dedup on model-generated questions, gap answers deliver 0 bits, finite-blocklength at n ≈ 16, a non-capacity-achieving protocol) — which only makes the p = 0.7-is-hard direction more robust.

## The error, and the refutation

The estimate wrote the feasibility criterion as `rounds · C(p) ≥ H(space)` with H(space) = the **open-ended everyday-noun space, ~10–13 bits**. That is wrong on the channel side. Fano's converse bounds reliable identification by `rounds · C ≥ H(X)`, where **H(X) is the entropy of the actual target distribution — the 8-word dev-8 set, H(X) = 3.0 bits** (verified, `RunStrongerCloser.scala` `devNames`). The ~10–13 bits is the entropy of the space the *decoder* searches because the protocol never reveals the target set (an `AgentActor` prompt property) — a **decoder** property, not a channel scarcity. The estimate double-counted decoder ignorance as channel scarcity.

Against the true H(X) = 3 bits, 1.9 bits does **not** forbid winning: a reasoner *told the 8 candidates* has single-guess success ≈ 2^1.9 / 8 ≈ **46%** (Fano permits success ≤ 84%). So "capacity ceiling for any reasoner" and "infeasible even for the known set" are retracted. The metric is a single guess, so the honest currency is a **success probability**, not a reliable-identification threshold: per-trial win ≈ 2^(rounds·C) / 2^H(decoder-space). At p = 0.7 against the open H ≈ 10 bits: 2^1.9 / 2^10 ≈ 0.0036, E[wins over N = 64] ≈ 0.23, P(0/64) ≈ 0.79 — so **0/64 is robustly predicted, but because the decoder faces the large open space, not because the channel forbids the 3-bit task.**

## What survives — the decomposition (the actual finding)

The stronger-closer null (W = S1 = D ≈ 0 at p = 0.7) has **two independently-binding causes**:

1. **The channel term (reasoner-independent).** 1.9 bits at p = 0.7 is fixed by physics; no *tier* reallocation manufactures channel bits (Shannon). This is why strengthening the closer/adversary/searcher does nothing — W, S1, D null *together*. This part is derived, and it is the one true thing the capacity view contributes.
2. **The decoder/search term (tier- and protocol-dependent).** The society searches the open ~10–13-bit space because the set is withheld, and its endgame is weak (E0). This is the **sole** cause at p = 1.0, where the channel is wide open (16 bits ≫ everything) yet the society still wins only **6/52 ≈ 12%**. So the decoder is independently binding, and a *better decoder* (a set-informed or search-stronger society) is not forbidden from winning at p = 0.7.

The doc's best sentence survives in weakened form: *a stronger tier cannot buy channel bits against a bad checker* — true and useful, but not *a stronger reasoner cannot win*, which the p = 1.0 decoder ceiling and the 3-bit target both contradict.

## Consequence for item 3 — the estimate's own fallback fires

Because the true target space is small (3 bits) and the frontier is not a clean capacity point (the decoder caps wins below it; the entropy the channel bound should use spans H(X) = 3 → open ~10–13), **no single derived cell placement is defensible**. The committee's refutation lands exactly on the doc's pre-registered fallback: item 3 becomes an **empirical staircase**, not a derived single point. Two pre-registrations the refutation forces onto item 3:

- **Falsifiability is asymmetric.** Capacity is cleanly testable only on the *infeasible* side (p = 0.7). On the *feasible* side, observed wins = capacity-feasibility × decoder-conversion, and the p = 1.0 ≈ 12% ceiling shows the decoder caps wins — so a feasible-cell *zero* does **not** refute feasibility (it is confounded with the decoder). The staircase can show a monotonic *rise* with C(p), not a clean frontier.
- **Power.** N ≈ 52–64 cannot resolve a 3–11% gradient (a true 5% over 52 games → 0–6 wins); the staircase supports only a coarse *monotonic-ordering* read, not a frontier-p estimate — locating a frontier would need N ≈ 150–200/cell.

## The decisive separating experiment (kept open by the committee)

The one experiment that would cleanly isolate channel from decoder is un-run: **reveal the 8-word target set to the society** (collapse the task to the 3-bit channel job) and check whether it still fails at p = 0.7. If it wins, the decoder/open-search was the binding constraint; if it still fails, the channel binds even the 3-bit task. This is more decisive than the staircase for the channel-vs-decoder question, and it is a prompt change, not a new harness.

*End — 2026-07-09 (committee-reconciled: REFUTED as a pure ceiling; kept as a decomposition).*
