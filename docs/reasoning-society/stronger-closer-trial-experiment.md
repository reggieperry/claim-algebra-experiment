# The stronger-closer trial — experiment specification

**Committee-reconciled (PROCEED_WITH_CHANGES, four lenses, 2026-07-09). Re-scoped: the well-posed, affordable question is the UNBLOCK — does allocating compute to the reasoning roles let the society sign at all at a degraded oracle, and is it the closer or the search that binds? The live diversity-vs-redundancy tradeoff is DEFERRED (kept hermetic), because it needs ~200–370 games/cell at Opus and the strong closer starves the very wrong-sign signal it reads.** Two runs (Sonnet-strong, Opus-strong) against a shared all-Haiku baseline, so the tier is a measured axis. 2026-07-09. Grounded in `RunSpendTradeoff` (the all-Haiku null), the composed cell (the template), E0 (the search diagnosis), and the committee review below.

> Status: **not yet implemented, not yet run.** This is the pre-registration. It goes to the operator for the scope confirmation (the tradeoff is deferred by committee recommendation — see the footer), then implementation + an adversarial verify, then the runs on an explicit go. Values marked *(tunable)* are the operator's.

---

## Why this cell exists

The composed cell certified the perfect-oracle corner and named the frontier: **capability under degradation.** The crown-jewel verification-spend tradeoff was measured only *hermetically*, because the live all-Haiku society at p = 0.7 (`RunSpendTradeoff`) **signed nothing** — no live signal to weigh. The results doc's conclusion: *a real fix needs a stronger reasoner.*

This trial supplies that reasoner by allocation, and asks the affordable question first: **does a stronger closer (and, diagnostically, a stronger searcher) unlock signing at a degraded oracle at all?** The expensive tradeoff is only worth funding if that unblock produces a healthy rate of wrong guesses reaching confirmation — which the unblock run measures directly.

## The question

**Primary — UNBLOCK (a two-proportion contrast, not a bare count).** Does the strong-arm society sign correctly at p = 0.7 at a rate distinguishable from the all-Haiku baseline? The all-Haiku null (`RunSpendTradeoff`: 0/16 per policy) is a single underpowered observation, fully consistent with a true rate ~0.05–0.08 — so "signed zero" is **not** an established floor, and the contrast must be powered as a difference in proportions, not "≥1 vs 0."

**Diagnostic — CLOSER or SEARCH?** p = 0.7 corrupts ~30% of *property* answers too (`ExperimentOracle.respond` → `model.corrupt` on every answer), so it degrades **search**, not just guess-confirmation; and E0 found the correct guess posed 0% of games even at a *perfect* oracle — implicating the **splitter/search** the design otherwise leaves cheap. So a third arm upgrades *everything* (splitter too): if it wins where strong-closer-only does not, the **searcher** is the binding role; if it also signs ~nothing, the blocker is architecture/search-under-noise, not the tier.

**Deferred — the live tradeoff.** Not in this run. The live per-game fail-open (`renderPrimary` divides by all N) is *not* the conditional hermetic rate the (1−p)^k curve represents; live it collapses by the wrong-guess-reach fraction *w*, and a strong closer suppresses *w* directly. The hermetic (k, ρ) curve stays the tradeoff's clean form (as the results doc concludes). This run *measures w* as a by-product; only if w is high enough is a conditional-denominator tradeoff worth funding later (see "The deferred tradeoff").

None of this speaks to the algebra — the independent variable is model allocation, so the finding is a **reasoner/capability** result, framed as such.

## Configuration

Seam-gated throughout (`corroborationSigns = false`), p = 0.7, `"correlated"` error model, k = 1, `maxRounds = 16`. Truth oracle Sonnet-5, held fixed. The three arms differ only in model allocation:

| arm | splitter (proposer) | driller (closer) | skeptic (adversary) | `difficulty` label |
|---|---|---|---|---|
| **W** — weak baseline | Haiku | Haiku | Haiku | `W-weak-k1` |
| **S1** — strong closer+adversary | Haiku | **strong** | **strong** | `S1-strong-k1` |
| **D** — strong everything (diagnostic) | **strong** | **strong** | **strong** | `D-all-k1` |

- **The strong tier is the run axis: two runs, Sonnet-5 then Opus** *(operator's decision)*. Exact enum `Model.CLAUDE_OPUS_4_8` (committee-cited as present in anthropic-java 2.47.0; **confirm at wiring**) and `Model.CLAUDE_SONNET_5`.
- **W is all-Haiku, so run ONCE and share** across both tier runs (tier-independent). S1 and D run per tier.
- **The tier must be real end-to-end** (committee must-fix): (a) distinct `difficulty` labels per arm (above) so `renderPrimary` never pools W with S1 — `SweepCell`/`GameRecord` carry no model field, so the label is the only separator; (b) add the **per-role model ids to `GameRecord` and `ConfigStamp`** (today the stamp hashes one `societyModel`); (c) derive the strong set from `AgentStrategy.driller.id` / `skeptic.id` (not bare string literals), and **assert at startup + log in the header that exactly the intended agents resolved to the strong tier and the rest to Haiku** — a typo that silently falls back to Haiku would reproduce the null and be misread as "the strong tier did not unblock" (a false negative).

- **Shared-W validity.** The one W run is a valid control for both tier runs **only if W, the Sonnet run, and the Opus run all execute under one config-surface stamp** — no code change (prompt, grader, budget, seam) between them. The runner asserts the three stamps are identical (else W is re-run per tier); a mid-sequence change would silently invalidate the shared control.
- **Cost-triage order** (if a mid-run budget cut forces a choice): run **S1-Opus before D-Opus** — the tier-split finding (outcome 4) needs S1 at *both* tiers, while the diagnostic D already replicates at Sonnet. Pre-committed so a cut does not improvise.

Targets: **dev-8**, **N per arm set by the power calc below** *(tunable)*. Live — Anthropic API key.

## Pre-registration — fixed before the run

1. **Unblock = a powered two-proportion test.** Success is the 95% CI on (S1 correct-rate − W correct-rate) excluding 0 — not "≥1 sign." The difference interval is the **Newcombe hybrid-score method** (Wilson-based, for the difference of two independent proportions), pre-committed here so the interval method is not chosen after the counts are known. Power W adequately (it may itself sign 1–2 by chance at N = 32). **Power/N (unblock contrast):** to distinguish a plausible W ≈ 0.06 from S1 ≈ 0.15–0.25 at 80% power needs roughly **N ≈ 60–180/arm** (δ-dependent); pre-register **N = 8/target = 64/arm** as the floor with a top-up to N = 16/target (128) if the CI straddles 0. This is the operator's cost dial.
2. **The diagnostic.** D vs S1: if D's correct-rate CI clears S1's, the **splitter/search** is the binding constraint (a cheap splitter caps the closer gain); if D ≈ S1, the closer/adversary allocation captures the available gain; if D ≈ W (both ~0), the blocker is architecture/search-under-noise, not the tier.
3. **W reproduces the null — with a pre-committed alarm.** The drift caveat fires **iff W's observed correct-rate exceeds 0.12** (twice the ~0.06 central estimate of the hypothesized 0.05–0.08 weak band, and below S1's 0.15 floor), pre-registered so the call is not made after seeing the counts; a W-rate in [0, 0.12] is the hypothesized weak baseline, not drift. **Null-model check (resolved in source):** `RunSpendTradeoff` ran the `"correlated"` model at p = 0.7 — `SweepCell(0.7, "correlated", …, k=1, ρ=0)` — so W matches it exactly: `SweepCell(0.7, "correlated", "W-weak-k1", k=1, ρ=0)`. (At k = 1, `"correlated"` with ρ = 0 is a single marginal flip at rate 1−p; ρ is inert with one confirmation.)
4. **Split the Opus gate.** Gate only the *deferred tradeoff* (should it ever run) on Sonnet signal — **always run the Opus unblock arms (S1, D) even if Sonnet nulls**, because null-Sonnet + positive-Opus is the tier-threshold finding (outcome 4), not a reason to skip.
5. **w is recorded.** From the archived logs, count games where a *wrong* guess reached a k-confirmation (the tradeoff's true denominator); report w per arm/tier. This is the datum that decides whether the deferred tradeoff is ever fundable.
6. **Tier ladder.** Sonnet and Opus compared on: did each unblock (S1 CI vs W), the diagnostic (D vs S1), the win rate, and w. A monotone Opus ≥ Sonnet ≥ Haiku is expected, not assumed.

## Outcomes and adjudication

1. **Unblocked, closer suffices (S1 > W, D ≈ S1).** Allocating compute to the closing/adversary roles unlocks the degraded regime; the cheap-proposer/strong-closer allocation is the demonstrated lever.
2. **Unblocked, but search binds (D > S1 ≥ W).** The *splitter* is the binding role under degradation; a strong closer alone is insufficient. Redirects the allocation principle.
3. **Not unblocked (D ≈ S1 ≈ W ≈ 0, either tier).** Capability under degradation is not gated by model tier at these roles — the architecture/search-under-noise binds. An informative null; the runs stop, no tradeoff.
4. **Tier split.** Opus unblocks where Sonnet does not — locates the capability threshold between the tiers.
5. **Anomaly — D < S1.** A strong splitter that *hurts* (over-decomposition — too-fine questions that fail to converge) would put the strong-everything arm *below* strong-closer-only. Unlikely, but not adjudicable from rates alone → read the archived logs.

## Caveats carried

- **Model allocation is a confound by design** — the agent under test changes (mixed tiers), so nothing here speaks to the algebra; it is a capability measurement of an allocation policy, architecture fixed.
- **Closer and adversary are upgraded together** (S1) — an unblock there cannot be attributed to the closer *alone*; the title's "closer" is shorthand for "closer+adversary." (An isolation cell — strong driller, Haiku skeptic — is a possible follow-up, not in this run.)
- **Society↔oracle alignment.** At p = 0.7 the strong arm makes the society Sonnet/Opus while the truth oracle stays Sonnet, so *property/search* answers now share a model between society and oracle; a search gain could be tier-capability or society↔oracle alignment. (The sign path is structural — sealed-truth guess-confirmation — so this does not touch the fail-open.)
- **The property-corruption channel** — p = 0.7 corrupts every answer, not only the guess-confirmation; the degradation is felt in search, which is why the diagnostic arm exists.
- **Degraded-corner scope** — one p (0.7); not a sweep.

## The deferred tradeoff (why, and what it would take)

The live diversity-vs-redundancy tradeoff is *not* in this run. To make it live would require: the **conditional** fail-open (denominator = games where a wrong guess reached k-confirmation, read from the logs — not `renderPrimary`'s divide-by-N), a conditional-denominator target of **m ≥ ~100** such games per cell, which at the achievable base rate needs **~200–370 total games/cell** (the corrected separation is 0.09 diversity vs **~0.24** redundancy at E3's ρ = 0.75 — not 0.30, which is the ρ = 1 monoculture — a materially harder detection), and it is fought by base-rate suppression (the strong closer that unblocks correct signs makes wrong commits rarer). It is worth funding **only if this run measures w high enough** to leave a wrong-sign signal. Until then the hermetic (k, ρ) curve stays the tradeoff's clean form.

## Acceptance (for the build, before the runs)

- `RunStrongerCloser(strongTier)` compiles under `-Werror`/Scalazzi; `sbt check` green; adversarially verified before any live spend.
- Per-role model allocation at the `AgentId => LlmCall` seam, derived from `AgentStrategy` ids; **startup assertion + header log** that exactly the intended agents resolved strong; per-role model ids on `GameRecord` + `ConfigStamp`; distinct `difficulty` labels per arm; per-path fail-open + correct split; w recorded from the logs; both arms' logs archived.
- Pre-registration items 1–6 fixed here, committed, before the runs.

---

## Committee reconciliation (2026-07-09)

Four lenses (design-validity, code-wiring, null-risk-adversarial, power-and-cost), each verifying against source; verdict **PROCEED_WITH_CHANGES**, high confidence, unanimous. Harness citations sound; the cheap unblock is worth running; the live tradeoff was refuted as sized. The six blocking changes are folded above:

1. **The live tradeoff is unmeasurable as sized** — `renderPrimary` is per-game, the hermetic anchors are conditional, so the k = 2 intervals need ~200–370 games/cell, and the strong closer starves the signal (H1 and H2 antagonistic). → Re-scoped to the unblock; tradeoff deferred + hermetic.
2. **The unblock criterion was inside the noise** ("≥1 vs 0" on an underpowered null). → A powered two-proportion CI test.
3. **W and S1 would silently pool** (no model field on `SweepCell`/`GameRecord`; `renderPrimary` groups by cell) and a seam typo falls back to Haiku unseen. → Distinct labels, per-role model ids on the record/stamp, a startup tier assertion.
4. **The bet may target the wrong role** (p = 0.7 degrades search; E0 implicates the splitter). → Added the strong-everything diagnostic arm.
5. **The Opus cost-gate foreclosed the tier-split finding.** → Split: always run the Opus unblock arms.
6. **The redundancy anchor was wrong** (0.24 at ρ = 0.75, not 0.30). → Corrected in the deferred-tradeoff section; verified by hand: (1−p)·[ρ + (1−ρ)(1−p)] = 0.3·0.825 = 0.2475.

Kept open (operator judgments, unresolved pre-run): whether the live tradeoff is ever worth its true Opus cost (this run's *w* informs it); closer vs search as the binding constraint (the diagnostic arm is built to resolve it); closer-vs-adversary attribution (bundled — an isolation cell is a possible follow-up). Correlation caveat: all four lenses are one Opus model family, so their convergence is strong internal signal, not independent confirmation; the load-bearing facts (roles, structural confirmation, the seam, the null, the anchor math) were each verified against source and re-checked by hand.

*End — 2026-07-09.*
