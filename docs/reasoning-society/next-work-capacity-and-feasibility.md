# Next work — mechanism confirmation, the capacity estimate, and the feasibility cell

**For Claude Code. Three items, ordered by cost: a minutes-scale log query that converts the composed cell's mechanism story from paired inference to within-game trace; a committee pass on a channel-capacity estimate that may upgrade the stronger-closer null from empirical to derived; and one pre-registered live cell that locates the feasibility frontier the capacity argument predicts. Items 1 and 2 gate nothing and can run immediately; item 3 is implemented only after item 2 reports, because its cell placement depends on the capacity numbers.** 2026-07-09. Grounded in the composed-cell log query (1 re-routed : 5 resurrected), the stronger-closer null (S1 = 0/64 at both tiers), and the w gradient (0.141 → 0.063 → 0.016).

> Execution scope: items 1 and 2 are analysis over existing artifacts — no new live games, no code changes beyond a read-only log script and a short derivation note. Item 3 is specification-then-build-then-run, with its own pre-registration section below; do not start its implementation until item 2's numbers are in and the operator confirms the cell placement. No floor, threshold, or prior result changes anywhere.

---

## Item 1 — the demotion-event query (minutes; do first)

**Why.** The composed cell's 1:5 re-routed/resurrected split was produced by pairing gated wins to open-arm games on the same `(target, seed)`. The seeds match the *oracle error draws*; the society's own sampling is live and stochastic. A matched pair with different outcomes therefore narrates a mechanism only if the gated log shows the intervention itself — a demoted 2-backer quorum event before the win. Without that check, the three abstain→win pairs may be sampling variance wearing a mechanism's name.

**The query.** Over the archived composed-cell logs, for each of the nine games below, report whether the gated-arm log contains a **demoted 2-backer quorum event** (a candidate reaching `backers ≥ 2` while `corroborationSigns = false`, hence not signing), and if so, on which candidate and at which event index:

- The 5 "resurrected" gated wins (including `dev spoon` and `held-out pencil`, the two named lie→win pairs, and the 3 abstain→win pairs).
- The 4 "attrition" games — the open arm's 2-backer winners whose gated twins did not win: did the gated twin reach a demoted quorum and then fail to confirm within budget (true re-route attrition), or never reach a quorum at all (variance)?

**Classification and reporting.** Each of the nine lands in exactly one bin: `mechanism-confirmed` (demotion event present, followed by the observed outcome), `variance-compatible` (no demotion event — the pair differs by sampling alone), or `other` (log shows something unclassified — quote it). Add one short paragraph to the composed-cell section of `fallible-oracle-results.md` with the bins, and adjust the mechanism language to match: any "resurrected" win without a demotion event is restated as "paired-outcome difference, mechanism not traced." If `spoon` and `pencil` show the event, name the event indices — those two become the program's cleanest existence proofs of a lie converted to a win, traced within-game.

**Acceptance.** The script is read-only over the archived logs; the nine classifications are printed with event indices; the results paragraph is updated under the same no-numbers-change rule as the prior corrections slice; the threats section needs no change (the pairing caveat is resolved, not added).

## Item 2 — the capacity estimate (a committee pass; no games)

**Why.** The stronger-closer null is currently argued from observation: a stronger reasoner works over the same corrupted premises, so tier does not unblock. There may be a derivation underneath it. At p = 0.7 each property answer is a binary channel with 30% crossover; capacity 1 − H(0.3) ≈ 0.119 bits/answer; sixteen rounds ≈ 1.9 bits ≈ distinguishing ~4 candidates — while the society's hypothesis space is open-ended. If that holds, correct identification at this (rounds, p) point is out of reach for **any** reasoner, and the null generalizes by Shannon rather than by induction over two tiers. The doc's best sentence — "past a point, a better reasoner does not buy soundness against a bad checker" — becomes a corollary instead of a summary.

**The pass.** Commission the committee (the four-lens pattern) to verify or correct the estimate against the actual protocol, attending to the places the back-of-envelope cheats:

1. **The confirmation channel adds capacity** — guess-confirmations are also answers (at p = 0.7, a 0.7-reliable equality test per guess). Account for the pose budget's contribution.
2. **Question independence under the `"correlated"` model** — per-question blind spots are deterministic, so re-asking is worthless but *fresh* questions are ~independent 0.7-reliable draws; confirm the per-fresh-question capacity treatment and whether compound questions change the accounting.
3. **The effective hypothesis space** — the society does not know the target set; estimate the entropy of the practical search space (the improvement's target vocabulary is a lower bound; the open-ended space is the honest denominator).
4. **The feasibility line** — derive rounds × capacity(p) ≥ H(space) as a curve in (rounds, p), and read off two points: the capacity-feasible p at 16 rounds, and the capacity-feasible rounds at p = 0.7. Predict where the trial's cells sit relative to the line (the p = 0.7 cells should sit clearly infeasible; p = 1.0 clearly feasible).

**Output.** A short derivation note (`capacity-estimate.md`), committee-reconciled, stating: the estimate, the corrections, the feasibility line, the two read-off points, and the honest scope (an order-of-magnitude bound on identification via noisy binary evidence, not a theorem about this exact protocol). If the committee refutes the estimate — the confirmation channel or the space entropy overturns it — that is the finding; item 3's placement then falls back to the empirical default below.

## Item 3 — the feasibility cell (one live run; implement only after item 2)

**Why.** The capacity argument makes a falsifiable prediction the tier ladder cannot: signing should unblock near the feasibility line, for *capability* reasons independent of model tier. One cell placed on the feasible side of the line tests whether the stronger-closer null is capacity (unblocks roughly where predicted) or something else (stays null even where capacity permits — which would point back at the architecture or the protocol and be the more surprising finding).

**Placement.** Set by item 2's line. Default if the estimate survives roughly as sketched: **p = 0.9, 16 rounds** (capacity ≈ 0.53 bits/answer × 16 ≈ 8.5 bits ≈ several hundred candidates — plausibly feasible). Fallback if the committee refutes the estimate: an empirical staircase instead — p ∈ {0.85, 0.9, 0.95} at small N to find where signing moves off zero, then power the found point.

**Design (the composed-cell/stronger-closer template, reused).** Seam-gated, k = 1, `"correlated"` model, dev-8 targets, arms W (all-Haiku) and D (strong-everything at the Sonnet tier — the cheaper strong tier, since the trial showed Opus adds nothing at these roles), both under one stamp, per-role model ids on the record, startup tier assertion, logs archived, w recorded. N = 64/arm floor with the same Newcombe/top-up discipline.

**Pre-registration (fixed before the run, in the cell's own spec file):**

1. **Primary contrast:** D's correct-rate at the feasible point vs D's 0/64 (Sonnet) at p = 0.7 — the same allocation, moved across the predicted line. Success = the Newcombe CI on the difference excludes 0.
2. **The capacity reading:** if D unblocks at the feasible point and W does not, the null was capacity-plus-capability (a strong reasoner can win where the channel permits); if **both** unblock, the p = 0.7 null was pure capacity and tier was never the variable; if **neither** unblocks where capacity clearly permits, the blocker is architecture or protocol — escalate to a log read before any further cells.
3. **W's role:** baseline at the same point; its drift alarm is the stronger-closer item 3 rule re-used (pre-commit the band once item 2 fixes the expected weak rate at the new p).
4. **No sweep:** one point (or the small staircase under the fallback), pre-registered as such; the frontier is *located*, not mapped, in this cell.

**Acceptance.** Spec file committed before implementation; `-Werror`/`sbt check` green; adversarial verify before live spend; the run lands in `fallible-oracle-results.md` as its own dated section under the threats structural rule.

## Sequencing and what not to do

- Order: item 1 (now) → item 2 (now, parallel) → operator confirms item 3's placement → item 3.
- **Do not** run additional tier arms (the ladder is measured shut at p = 0.7), re-run any prior cell, lower any floor, or attempt the deferred verification-spend tradeoff — the w gradient just re-confirmed its starvation; it stays hermetic.
- The dev-harness track is independent and unaffected; if the ledger is live in this repo, each item's completion report should enter it as an assertion and be discharged by the item's own acceptance check.

## Status ledger

- **Item 1 output:** nine games binned mechanism-confirmed / variance-compatible / other; results paragraph adjusted to the traced truth.
- **Item 2 output:** `capacity-estimate.md`, committee-reconciled — the estimate verified, corrected, or refuted; the feasibility line and two read-off points.
- **Item 3 output (conditional):** one pre-registered cell at the feasible point; the null's nature adjudicated — capacity, capability, or architecture.
- **Standing after all three:** the fallible-oracle program is either complete (null explained, frontier located, mechanism traced) or has surfaced the one thing that would reopen it (a null where capacity permits signing).

*End — 2026-07-09.*
