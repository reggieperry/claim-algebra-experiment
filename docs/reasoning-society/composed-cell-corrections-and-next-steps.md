# Composed-cell reporting corrections and next steps

**For Claude Code. Four corrections to `fallible-oracle-results.md` (and the HTML report where it mirrors them), one log analysis that adjudicates a mechanism the cell surfaced, and the sequencing that follows. The measurements stand as run — every table recomputes clean, pre-registration item 1 is met exactly, and no numbers change. These corrections fix what the write-up says about the numbers, one deviation it should declare, and an appendix that has rotted across three revisions.** 2026-07-09. Grounded in the composed-cell table (`RunComposedCell`, stamp `e4666df175ff43e3`), the pre-registration (`fallible-oracle-composed-cell-experiment.md`), and Run A (`RunWinRate` / `RunEndgameAudit`).

---

## Correction 1 — the win result is a rise, and the pre-registered structural claim failed benignly

**What the doc says.** "Seam-gated sign-correct 6/52 vs seam-open 5/52, a drop of −1, inside the pre-committed benign ceiling of 5," with the mechanism given as "the demoted 2-backer winners re-routed to the oracle and signed there rather than vanishing."

**What the numbers say.** 6 > 5. Gated wins *rose*. "A drop of −1" states a rise in the vocabulary of the loss that was expected, and the expectation itself — the pre-registration's "gated wins are structurally ≤ open wins" — is refuted by the measurement. The ≤ argument was incomplete, not unlucky: it accounted for re-routed winners but not for **resurrected games**. In the open arm a 2-backer wrong sign terminates the game as a fail-open; under gating the same game is demoted, continues, and can win. Fifteen open-arm games ended in 2-backer lies; gated, those fifteen kept playing.

**The held-out split proves the resurrection mechanism is required, not optional.** Open held-out had zero wins of any path (`correct 0.00`), so the two gated held-out wins cannot be re-routed winners — there was nothing to re-route. The doc's re-routing sentence explains dev-8 at most and cannot explain held-out at all.

**Edits to make:**

1. In the composed-cell section, replace "a drop of −1, inside the pre-committed benign ceiling of 5" with language that states the direction plainly: gated wins exceeded open wins by one (6/52 vs 5/52), against the pre-registered structural expectation of ≤, which is hereby noted as refuted; the benign-ceiling test is moot because there was no loss to bound.
2. Replace the single-mechanism sentence with the two-mechanism form: gated correct signs come from (a) demoted 2-backer winners re-routing to the oracle, and (b) games that would have terminated in a 2-backer fail-open continuing under gating and winning — with the held-out arm (2 gated wins where the open arm had 0) as the existence proof of (b).
3. Add the upgraded finding to the summary bullet: **fail-closed is productive, not merely safe** — closing the lying path recycles would-be lies into additional attempts, and some become wins. The strongest supported sentence becomes: *at perfect check reliability, closing the unchecked path zeroes the lies and slightly increases the wins, because games that would have ended in a lie keep playing.*
4. In `fallible-oracle-composed-cell-experiment.md`, annotate the "structurally ≤" line inline (do not silently edit it): superseded by measurement, pointer to the results section — the same supersession style the endgame section already uses.

## Correction 2 — state the win-side control miss

Pre-registration item 5 set two reproduction targets for the seam-open control arm: Run A's fail-open (0.22–0.30) and Run A's wins (0.15–0.19), with the rule that a miss gets the version-drift caveat stated. The doc reports the fail-open side (0.25 / 0.35 — reproduced) and is silent on the win side, where held-out came in at **0.00 against Run A's 0.15**. At 3/20 vs 0/20 this is plausibly sampling noise, but the protocol does not say "unless probably noise."

**Edit:** one sentence in the composed-cell section after the control claim: the open arm reproduced Run A's fail-open on both sets but not Run A's held-out win rate (0.00 vs 0.15; 0 of 20 vs 3 of 20, compatible with sampling noise at this N); per pre-registration item 5 the win-side contrast against Run A carries a version-drift caveat, while the within-run contrast — the one the cell was designed around — is unaffected.

## Correction 3 — declare the pre-pull deviation

Pre-registration item 3 registered the benign-loss ceiling as **Run A's** correct-`BackerQuorum` count, pulled from its records before the run. The doc took the ceiling from the **within-run open arm** (`co:bk = 5`), read before the gated arm. That is arguably the better number — same stamp, same version — but it is a deviation from the registered procedure, and pre-registration only means something if deviations are declared rather than silently improved upon.

**Edit:** one clause where the ceiling is introduced: "ceiling taken from the within-run control rather than Run A's records as registered, for stamp-consistency — a declared deviation." If Run A's own count was also pulled, print both.

## Correction 4 — the threats-to-validity section has rotted; fix it structurally

Three claims in the appendix are contradicted by the document's own body, and this is the third consecutive revision in which they have survived:

- "The correlation ρ is dialed, not measured" — E3 measured it (ρ ≈ 0.75 / 0.51 / →0); the E3 section itself says the dialed correlation became a number.
- "Sign-correct is 0 across the sweep" — true only of the original sweep; the endgame improvement and the composed cell both moved it.
- "12 games per cell" — predates a 104-game cell.

**Edits:**

1. Scope each threat to the arm it describes (e.g., "Original sweep: N=12/cell…"; "ρ was dialed in the hermetic curve and subsequently measured in E3; the hermetic curve's external validity now rests on E3's N=24").
2. Add the composed cell's own threats, currently absent: per-target n of 2–4 supports set-level statements only; the fail-open metric remains relative to `TargetMatch`'s synonym table; the p = 1.0 corner scope (gating C leaves O, and O is the entire fail-open under degradation).
3. Adopt the structural rule that prevents recurrence: **the threats section is part of every revision's acceptance** — each new measured section either updates the threats it invalidates or dates them to the arm they describe. This is the librarian rule applied to the document itself; the appendix was carrying defeated claims on the live board.

## Next step 1 — the log query: re-routed vs resurrected (do this first; it is minutes)

The archived per-game event logs for both arms adjudicate Correction 1's mechanism split. For each of the 6 gated wins, find the open-arm game on the same target (and seed, if the arms share per-game seeds; by target-instance otherwise) and classify:

- **Re-routed:** the open-arm counterpart *won via 2-backer* → the winner changed path.
- **Resurrected:** the open-arm counterpart *fail-opened via 2-backer* (or abstained) → gating kept a would-be-terminated game alive to a win.

Report the 6 wins as `re-routed : resurrected : other`, per set (dev-8 / held-out-10). Prediction to hold the query against: held-out's 2 are resurrected by necessity; dev-8's 4 are some mix. Add one paragraph to the composed-cell section with the split, and — if any gated win's counterpart was an open-arm *fail-open on the same target* — name one such pair explicitly as the concrete instance of a lie converted to a win. Also pull the inverse for completeness: how many of the open arm's 5 `co:bk` winners failed to win under gating (pushed to Ambiguous or budget-exhausted), which prices the re-route attrition the committee's ≤ argument was about.

## Next step 2 — one-slice doc pass, then stop

Apply Corrections 1–4 and the log-query paragraph as **one revision slice** to `fallible-oracle-results.md` and regenerate the HTML from it. Acceptance for the slice: the four edits present; the supersession annotation added to the spec file; the threats section passes the new structural rule (no claim in it contradicted by the body); no measured number changed anywhere; `sbt check` untouched (this is a docs slice).

## Sequencing after the slice

1. **The frontier is officially capability under degradation.** E1 (the good-but-imperfect band) stays blocked exactly as the results state — the improved system cannot yet generate confirmed signs at p < 1.0 (`RunSpendTradeoff`, all-abstain). Unblocking is a **stronger-closer trial**: same architecture, same gate, a stronger model in the closing role only (the model-allocation principle: cheap proposers, strong adversary, strong closer). That is a purchase decision plus one config change, not a design task; it should get its own small pre-registered cell when budgeted, with the composed cell's design (both arms, stamp, per-path splits) as the template.
2. **Do not** re-run any prior arm, lower any floor, or widen the sweep; the architecture side of the toy is measured shut at this corner, and the two-path enumeration survives its strongest test to date.
3. The dev-harness track (ledger, gate, audit) proceeds independently and is unaffected by anything here.

## Status ledger

- **Confirmed by the cell:** pooled gated fail-open 0/52 [0.000–0.069], excluding 0.22; every open-arm fail-open on the 2-backer path (`fo:or = 0`); no third sign path at 95%-ish surfacing power for the pooled N.
- **Refuted by the cell:** the coupling attribution (nudge → confident-wrongs), and the pre-registered "gated wins ≤ open wins" structural claim.
- **Upgraded by the cell:** fail-closed is productive — the resurrection mechanism, pending the log-query split for its exact share.
- **Deviations to declare:** the pre-pull ceiling source (Correction 3); the win-side control miss (Correction 2).
- **Open by design:** capability under degradation; the quantitative Arm 3 tradeoff; both behind the stronger-closer trial.

*End — 2026-07-09.*
