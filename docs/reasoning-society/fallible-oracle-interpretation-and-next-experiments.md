# The fallible-oracle experiment — interpretation and next experiments

**Reading of the measured results in `fallible-oracle-results.md`, and the experiments they now motivate.** 2026-07-08. The design registered the plan; the results recorded the run; this says what the run *means* and what to run next. Written in the fair-control register: it separates what the data established from what it only argued, and it treats the most important finding as the one that reframes the question rather than the one that confirms the prediction.

---

## What the run answered

The main question was never "does the toy play Twenty Questions well." It was the project's central question shrunk to a bench: **does the fail-closed guarantee survive a fallible check?** The perfect oracle had been hiding that question — "confidence attaches only at a non-generative check, and the gate fails closed" is trivially true when the check is flawless. It becomes a real test only when the check can lie.

The run answered it in a specific, partial, and more interesting way than either predicted branch:

> **On the sign path that consults ground truth, fail-closed survives degradation gracefully. But the run discovered a second sign path that consults nothing — and that path lies even when the oracle is perfect.** The answer is not "graceful" or "catastrophic." It is: the guarantee holds exactly as far as the checking does, and the system contains a place where it certifies without checking.

Everything below is the unpacking of that sentence.

### The fail-closed reflex held where it was actually tested

Where a sign passed through the oracle, the architecture behaved as designed. As reliability fell, the loss went to **abstention**, not to wrong signatures — the gate declined rather than lied. And the mitigation behaved to the decimal: the oracle-confirmed fail-open fell as (1−p)^k when confirmations were independent (k=2 measured 0.09 against a predicted 0.09; k=3, 0.02 against 0.027) and stayed flat at ~(1−p) when they were correlated. On the checked path, a fallible check degrades into honest silence, and redundancy buys safety in proportion to independence. That is the design's premise, confirmed.

### The seam: a disjunctive `verify()` is only as strong as its weakest branch

The result that matters most is not on the degradation axis at all. At a **perfect** oracle the system still signed wrong candidates — every such fail-open a **2-backer** sign, two agents agreeing on a wrong answer through a corroboration path that never consults the oracle. This is deductive, not a small-sample artifact: at p=1.0 a wrong guess is answered "No" and struck, so an oracle-confirmed wrong sign is structurally impossible there; the survivors *must* be the un-checked path. The existence of that path is therefore established even though its rate (0.17, 2/12, wide interval) is not.

Stated in the algebra, the finding is exact. The accept biconditional is `accept ⟺ corner=T ∧ cardinality=1 ∧ grade≥θ ∧ verify`, and the run reveals that `verify` in the toy is a **disjunction** — satisfied by *either* two-agent corroboration *or* oracle confirmation, `verify = C ∨ O`. A disjunction is satisfied by its weakest satisfiable branch. `O` (ground truth) is strong; `C` (agreement between two generative sources) is weak — it can be true when the candidate is false, whenever two agents are wrong together. So `verify = C ∨ O` can fire on a false candidate through `C` even when `O` would have caught it, and the gate signs a falsehood at a perfect oracle. **The system's effective guarantee is the weaker of its two sign paths.**

This is the precise concern I raised reviewing the B1 instructions — that the corroboration floor might be a *separate* `verify` satisfier rather than the conjunct itself, and that it needed checking against `GameCore.decide` rather than asserting. The experiment answered it by measurement: it is separate, it is generative, and it is where the system lies regardless of oracle quality. The design review could only flag the possibility; the run turned it into a measured seam.

### The unit of trust is the sign path, not the gate

The interpretive move the run forces — and the one worth carrying to every future version and to the real system — is this: **"is the gate fail-closed?" is the wrong question.** The gate has more than one way to satisfy `verify`, each is a distinct trust assumption with its own failure mode, and the gate is only as trustworthy as its weakest satisfiable path. The oracle path trusts ground truth (fails only when the oracle errs). The corroboration path trusts inter-agent agreement (fails whenever correlated agents share a blind spot, *independent of oracle quality*). Fail-closedness is not a property of the gate; it is a property of each sign path, and the system inherits the weakest one.

This transfers directly and is the reason the toy is worth the trouble. Claude Code's `verify()` will be exactly this kind of disjunction — tests pass, *or* types check, *or* a reviewer approves — and the real failure will be whichever satisfier certifies on the weakest evidence, not the one you were watching. The design discipline the run recommends is therefore not "make the check better" but **enumerate the sign paths, and for each name what it trusts and whether that thing can be wrong.** The weakest path is the system's real guarantee.

### The two monocultures are one failure

Both fail-open sources are the same mistake at two layers: **trusting correlated generative sources as if they were independent.** At the agent layer, two Haiku instances agreeing is a corroboration floor that thinks it has two independent backers and has two correlated ones. At the confirmation layer, N Claude confirmations of the same guess collapse the (1−p)^k benefit toward (1−p) as their errors correlate. The lesson is identical at both layers and it is the transferable one: **check diversity beats check quantity.** Stacking more of the same kind of check does not buy the geometric safety the redundancy intuition promises, because the thing that makes redundancy work — independence — is exactly what a monoculture lacks.

### The useful half of fail-closed is untested

One limit governs how far any of this can be pushed. **Sign-correct was zero at every cell, including at a perfect oracle on an easy target.** Fail-closed properly means "abstain when unsure *and* sign correctly when sure"; the run tested only the first clause. With no correct signs anywhere, **"appropriately cautious" and "broken endgame" are observationally identical in this data** — a system that abstains 83–100% of the time and wins 0% might be beautifully selective or might have a dead win path, and these numbers cannot tell them apart, precisely because there are no wins to prove the caution is selective. That the society could not close "dog" with a *perfect* checker in twelve rounds points at an endgame problem sitting underneath the whole degradation question, and it must be resolved before "graceful" can mean anything stronger than "does not lie."

### The honest ledger: measured vs. argued

Keeping these separate is the whole point of the exercise.

- **Measured, clean.** The redundancy/correlation curve (hermetic, N=800, matches the mechanism). The Theorem 6.7 arm (three correctness assertions: on-path gap blocks even when the guess was secretly right, off-path gap routes around, the `Unknown` mechanics underneath). Abstention dominance across the sweep. The *existence* of the un-checked corroboration path (deductive at p=1.0).
- **Argued, not established.** That the 2-backer path *dominates* at a good oracle — the p=1.0 all-2-backer result is deductive (oracle-confirmed is impossible there), not evidence of relative dominance, and across the totals there were more oracle-confirmed fail-opens (4) than 2-backer (2); the good-but-imperfect regime where both compete is unsampled. Arm 3's spending direction (sensible, follows from the seam, but "settle its direction" overstates a mechanism argument). The *magnitude* of the seam (2/12, wide interval).

---

## Next experiments

The next phase is not a wider reliability sweep. It is a **sign-path audit**: for each way the gate can sign, drive it to fail-open as cheaply as possible and name what it was trusting. The run found two paths and audited neither to exhaustion; it characterized their regimes and left the seam open. The experiments below are ordered by dependency — the first is a prerequisite, because a system with no correct signs cannot exhibit the phenomenon whose degradation the rest are trying to study.

### E0 — the endgame diagnostic (prerequisite: make correct signs observable)

**Question.** Does the win path fire at all, and why is sign-correct zero even at a perfect oracle?

**Why the run motivates it.** With zero wins, fail-closed and broken are indistinguishable, and every downstream degradation claim is uninterpretable. Neither results file carries the one breakdown that would resolve it: were the abstentions *never posed a guess*, or *posed a guess and got "No"*? Those are different diseases — the first is a convergence/endgame failure (the society never reaches a lone candidate to guess), the second is a search failure (it guesses, wrongly, and honestly abstains).

**Design.** Instrument the abstention outcome to record, per game, whether B1 ever posed a guess, and if so how many and against which candidates. Re-run the p=1.0 cell on an *easy* target set (dog, chair, water) with a larger N and, as a control, a longer round budget (20, 30). Independent variable: round budget and target difficulty. Dependent: fraction of games where a guess was posed; fraction where a correct guess was posed and signed.

**Predictions and meaning.** If wins appear once the budget is loosened → the endgame works but was starved; the twelve-round budget was the constraint, and the earlier "graceful/never-wins" reading was a budget artifact. If guesses are posed but never correct even at a perfect oracle → the search cannot narrow at Haiku tier, and the architecture-level findings stand but the toy needs a stronger proposer to study degradation of a *working* system. If guesses are rarely posed at all → the convergence-to-guess machinery (the B1 trigger) is the bottleneck, and that is the next fix, prior to any oracle work. Each outcome redirects the program differently, which is why this runs first.

### E1 — the good-but-imperfect regime (characterize where both sources compete)

**Question.** In the realistic band (p ≈ 0.85–0.95), which fail-open source dominates, and is the 2-backer floor really oracle-independent?

**Why the run motivates it.** The sweep sampled 1.0 (deductive, uninformative about relative dominance), 0.7 (zero fail-opens of either kind), and 0.5 (bad-oracle regime). The band that matters — a checker that is usually right and occasionally wrong, which is what a real test suite is — was never sampled, and it is exactly where both fail-open sources are live and can be compared. Arm 3's premise lives or dies here.

**Design.** Run cells at p ∈ {0.95, 0.9, 0.85}, k=1, with N large enough to resolve a difference between the two fail-open sources (the results' own power analysis implies hundreds per cell). Contingent on E0 having produced a nonzero win rate, so difficulty is tuned to make wins possible. Dependent variables: the two-source split of the fail-open (2-backer vs oracle-confirmed), and the operating-envelope pair (fail-open, abstention).

**Predictions and meaning.** The mechanism predicts the 2-backer component is roughly flat across this band (it never consults the oracle) while the oracle-confirmed component scales with (1−p) → so 2-backer should overtake oracle-confirmed as p rises toward 1, confirming Arm 3's premise *with data* rather than by extrapolation from a tautology. If instead the 2-backer component also falls as p rises, the two sources are coupled through the search (corrupted search both misleads guesses and prevents agent agreement), which would be a new and more tangled finding and would complicate the "independent floor" reading.

### E2 — close the seam and re-measure (fix-and-verify the headline finding)

**Question.** Does gating the corroboration path with an *independent* check remove the perfect-oracle fail-open?

**Why the run motivates it.** This is the direct answer to the seam. The finding is that `verify = C ∨ O` fires unchecked through the weak branch `C`. The fix, in the algebra, is to stop treating `C` as a standalone satisfier: either drop it (only the oracle signs), or gate it so that a corroboration-signed candidate must also pass an *independent* check — a different model family, a mechanical test, or a human on the two-agent agreement — turning the weak disjunct into a checked one. Turning "we found a hole" into "we closed it and measured the closure" is the natural completion of the result.

**Design.** Implement the gated-corroboration variant (proposal: a corroboration sign must be confirmed by a checker drawn from a *different* model family than the two backers, so the confirmation's blind spots are uncorrelated with theirs). Re-run the p=1.0 cell. Independent variable: seam-open vs seam-gated. Dependent: perfect-oracle fail-open rate and its source split.

**Prediction and meaning.** If the fix works, perfect-oracle fail-open falls to ~0 — the un-checked path no longer signs, and the only remaining fail-open at p=1.0 is whatever slips through the independent check, which should be rare if the check is genuinely independent. A clean falsification target: if perfect-oracle fail-open does *not* drop, either the "independent" checker was not actually independent (its errors correlate with the backers' — a diversity failure, itself a finding), or the fail-open was entering through a path this fix does not reach, and the sign-path audit is incomplete.

### E3 — measure the real correlation of redundant checks (make the transfer quantitative)

**Question.** What is the actual error-correlation ρ between two Claude instances confirming the same claim — same model, and across model families?

**Why the run motivates it.** The redundancy curve is the strongest result in the study, but ρ was *dialed*, not measured: it shows the consequence of a given correlation, never the correlation real checks exhibit. The entire transfer to the real system — "stacking correlated Claude checks does not buy the geometric safety" — currently rests on an *assumed* high ρ. This experiment measures it, and in doing so tests the "diversity over quantity" claim directly.

**Design.** Assemble a set of guess-confirmations with known ground truth (from logged games, including deliberately hard/ambiguous ones). Pose each to two confirmers and record their joint error: (a) two instances of the same model, (b) two different model families, (c) a model and a mechanical/rule check. Estimate ρ from the rate of joint errors relative to the product of marginal error rates. Independent variable: confirmer pairing. Dependent: measured ρ, and the implied redundancy benefit (1−p)^k-vs-flat placed on the earlier curve.

**Prediction and meaning.** The expectation is that same-model ρ is high (near the flat, redundancy-useless end of the curve) and different-family ρ is substantially lower (nearer the geometric end). If confirmed, this is the empirical case for diversity — it converts "if correlated, redundancy fails" into "Claude-same-model confirmers correlate at ρ≈X, so a second one buys almost nothing, while a different family at ρ≈Y buys most of the geometric benefit," which is a directly actionable design number for the real gate. If different-family ρ is *also* high — if all capable models share the same blind spots on hard cases — that is the deeper and more sobering finding: diversity of *model* is not diversity of *error*, and the only genuinely independent check is a non-generative one (a test, a proof, a human), which would be the strongest possible vindication of the original thesis that confidence must attach at a *non-generative* check.

---

## What not to do next

- **Do not widen the reliability sweep before E0.** Studying the degradation of a system that never succeeds measures nothing; the sweep's shape is uninterpretable until there is a nonzero win rate to degrade.
- **Do not lower any sign floor.** Unchanged from the design's standing rule and reinforced by the results: the floor is the last guard between a poisoned game and a confident-wrong signature; the seam is fixed by *adding* an independent check, never by *weakening* a quorum.
- **Do not add more same-family confirmations.** The ρ curve already shows that quantity of correlated checks does not buy safety; the correct response to the seam is an *independent* check (E2), not a higher quorum of the same kind.
- **Do not build search features (multi-slot decomposition, richer question models) to chase the win rate before E0 diagnoses it.** The zero-win result may be a starved budget or a broken B1 trigger, not a search-quality problem; fix the diagnosed cause, not the assumed one.

## Faithfulness note

Nothing proposed here weakens the gate; the sign-path audit *tightens* it. The seam finding is stated exactly in the accept biconditional — `verify` is a disjunction whose weak branch (`C`, generative agreement) fires unchecked — and the fix (E2) converts that disjunction toward a conjunction of independent checks, which strictly narrows what can sign. The redundancy and 6.7 results stand as measured. The one interpretive claim that goes beyond the data — that the unit of trust is the sign path rather than the gate — is offered as a lens, not a measurement, and E2 is its first test: if gating one path closes its fail-open and no other opens, the lens holds. The gate remains the sole signer, fail-closed; the program's next job is to find every place it currently signs without checking, and close each one.

*End — 2026-07-08.*
