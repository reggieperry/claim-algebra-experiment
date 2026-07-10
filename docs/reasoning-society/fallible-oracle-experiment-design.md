# The fallible-oracle experiment — design, testing, and analysis

**A controlled study of where confidence attaches when the check itself is unreliable.**
*2026-07-08 · for the Twenty Questions observability toy, after B1 (the oracle-checked guess) ships · builds on the fold-over-log, the belief-inert oracle answer, the accept biconditional (`claim-algebra.html` §4.4), Theorem 6.7 (gap-only fail-closed propagation), and the correlated-errors / monoculture risk (society-of-minds Part V) · this is a design and analysis plan, not results — the curves are the deliverable of running it*

---

## The question, and why the toy cannot currently ask it

Twenty Questions works because its oracle — the human holding the secret — is **cheap, perfect, always available, and always correct.** All four are false of the check you actually care about. Claude Code's `verify()` (the test suite, the type-checker, the human reviewer) is fallible (tests pass and the code is still wrong), partial (coverage has holes), and expensive (the full suite costs time). The whole architecture rests on one claim — *confidence attaches only at a non-generative check, and the gate is fail-closed* — and that claim is **trivially satisfiable when the check is a perfect oracle.** It only becomes a real test of the design when the check can be wrong.

So the next step is not to make Twenty Questions play better. It is to **break the oracle's perfection, minimally and parametrically, and measure what the architecture does about it.** The single sharpest question, stated as a falsifiable pair:

- **H_graceful.** As oracle reliability falls, the lost reliability is absorbed by **abstention** — the system declines to sign more often, but the rate at which it signs a *false* answer stays near zero. The architecture's guards convert an unreliable check into honest silence.
- **H_catastrophic.** As oracle reliability falls, the lost reliability passes straight through to **fail-open** — the rate of signing a false answer tracks the oracle's error rate. The gate is a pass-through for the check's mistakes; the architecture provides no protection.

Everything below is built to distinguish these two, to measure the slope between them, and to find out which of the architecture's guards (the corroboration floor, the Skeptic, redundant confirmation) actually moves that slope.

**B1 is a hard prerequisite.** The fail-open event lives at exactly one place: the guess-confirmation, where the oracle's "Is it X? — Yes" discharges the `verify()` conjunct of the accept biconditional. Before B1 there is no oracle in `verify()` — the gate signs on internal agent quorum — so a fallible oracle can only corrupt *search*, never *sign*, and the central measurement has no place to happen. This experiment is therefore downstream of B1 in the fix sequence: it cannot run its main arm until the oracle-checked guess exists.

---

## Design

### The one mechanism change: split the oracle role

Today the human *is* ground truth — `HumanMove = Answer | Challenge` (`Oracle.scala`), and the answer the society sees is by definition correct. To make the oracle fallible, split the single role into two:

- **The experimenter (ground truth).** Registers the true target at game start. This value is held by the harness and **never enters the society's view** — not the transcript, not a prompt, nothing. It exists only to adjudicate outcomes.
- **The oracle channel (what the society sees).** Answers property questions and B1 guess-questions by consulting the true target and then applying an **error model**. Its answer may differ from the truth. This is the value that flows into the transcript, gets rendered into prompts, and drives the agents' `Assert` / `Corroborate` / `Refute` events.

This split preserves the governing fact from the recovery work: **the oracle answer is belief-inert.** A corrupted answer never enters the belief fold directly; it misleads the *agents*, who then emit the events that move belief. That is why a wrong property answer degrades search but cannot, by itself, manufacture a wrong sign — the sign can only go wrong at `verify()`, i.e. at the guess-confirmation.

**The measurement apparatus falls out of the existing design.** Record three things in the event log as belief-inert markers: the registered true target, each oracle answer, and (for reproducibility) the error draw that produced it. Then every game is fully replayable, and **classification is a pure fold over the log**: compare the signed slot, if any, to the recorded true target. No new measurement machinery — the same log-centric discipline as the rest of the system, now pointed at itself.

### Independent variables

| Variable | Range | Role |
|---|---|---|
| Oracle reliability `p` | 1.0 → ~0.5 (e.g. 1.0, 0.95, 0.9, 0.8, 0.7, 0.6) | The main sweep. `1 − p` is the per-answer error probability. |
| Error model | independent-uniform · systematic-per-question · correlated-across-channels | *How* the oracle is wrong — the decisive design choice (see below). |
| Redundancy `k` | 1, 2, 3 | Number of independent guess-confirmations required before the gate signs. |
| Correlation `ρ` | 0 → 1 | How correlated the `k` confirmations' errors are. Rides on the redundancy study. |
| Arm | fallible · partial · expensive | Which imperfection is being injected. |

**The error model is the adversary, and a weak adversary corrupts the measurement.** This is the same principle already settled this session for proposer-vs-adversary allocation: a check that is too easy to defeat flatters the thing under test. An **independent-uniform** error (each answer flips i.i.d. with probability `1 − p`) is the *optimistic* case — errors are uncorrelated, so redundancy and averaging work well. It is also *unrealistic*: real checks are not randomly wrong, they are wrong in the same places every time. The **systematic-per-question** model — the oracle is wrong on a fixed subset of (question, truth) pairs, and re-asking the same question yields the same (possibly wrong) answer — mirrors a check with blind spots and is the honest test. Lead all conclusions with the systematic model; use independent-uniform only as an upper bound on how well the architecture could possibly do. The gap between the two curves is itself a finding: how much does the realism of the error model change the verdict.

### Dependent variables

Each game resolves to exactly one primary outcome, adjudicated against the recorded truth:

- **SIGN-CORRECT** — the gate signed slot `C` and `C` equals the true target.
- **SIGN-WRONG** — the gate signed slot `C` and `C` differs from the true target. **This is the fail-open, the cardinal metric.** It is the negation of the accept biconditional's soundness under a corrupted `verify()`.
- **ABSTAIN** — the gate signed nothing (inconclusive / budget exhausted, now carrying A2's tentative read).

Adjudication is **mechanical, not semantic** — consistent with librarian non-generativity. The target set is pre-registered with canonical labels; the sign is a slot with a label; SIGN-WRONG holds iff the labels differ. No judgment call, no similarity threshold.

Over `N` games at each cell we compute three rates — **fail-open rate**, **abstention rate**, **sign-correct rate** — and two derived objects that carry most of the analytic weight:

- **The two-factor decomposition of fail-open.** A wrong sign requires *both* that the society poses a wrong guess *and* that the oracle erroneously confirms it: `P(SIGN-WRONG) = P(wrong guess posed) × P(oracle confirms | wrong guess)`. The second factor is essentially the oracle's false-positive rate on guesses (≈ `1 − p` under independent error); the first rises as `p` falls, because corrupted search sends more wrong candidates to the guess stage. Decomposing lets us see *which* factor drives any catastrophe rather than reporting an opaque rate.
- **The operating envelope.** Zero fail-open is trivially achievable by abstaining always, so the headline is never fail-open alone — it is the **joint** `(fail-open, abstention)` pair as `p` varies. A useful architecture keeps fail-open low *without* driving abstention to 1. Plotting the joint curve shows whether falling reliability moves the system along the honest axis ("abstain more, stay truthful") or the dishonest axis ("sign wrong").

### Controls and hygiene

- **Fixed, pre-registered target set, stratified by difficulty.** Winnability varies by target — `dog` is easy, `apple` is hard given the material taxonomy. Hold the target mix constant across every `p` so a reliability effect cannot be a difficulty-mix artifact. Stratify the analysis by difficulty tier so the two are separable.
- **Seed the new stochastic element.** Determinacy 4.1 already makes the society side reproducible (same log prefix → same fold). The oracle's error draws are the *new* randomness; seed them and log them so any game is bit-for-bit reconstructible.
- **Test the system as built.** B1, A1–A3, and the convergence monitor are all in play. This measures what you would actually ship, not a bare gate — that is the right object, but state it, because the guards are part of what the experiment is testing.
- **Power for rare events.** If fail-open is genuinely rare (the graceful case), estimating *how* rare with tight confidence intervals needs real `N` — hundreds of games per cell on the low-error end. Budget for it or report honestly wide intervals there.

### The arms

**Arm 1 — Fallible (centerpiece).** The oracle flips answers per the error model. Run the full `p` sweep under each error model. This arm produces the primary curves and hosts the redundancy/correlation study. It is the main event because the fallible oracle is the closest parametric analogue of a real, passably-good-but-imperfect check.

**Arm 2 — Partial (the Theorem 6.7 arm).** The oracle sometimes returns "I don't know" (a genuine gap, the `N` corner) instead of yes/no. In the current toy gaps never occur — the human always answers — so 6.7's fail-closed *gap* propagation has only ever held by construction, never been exercised in a running system. This arm is a **correctness check, not a degradation sweep**: a gap on a question the eventual guess *depends on* must propagate to abstention and never to a sign (6.7). The boundary is the interesting part — a gap on a question the guess does *not* depend on (off the derivation path) must *not* block; the society should route around it. Only on-path gaps block, because 6.7 is gap-only *and* path-local. A handful of targeted scenarios, not a large grid.

**Arm 3 — Expensive (verification budgeting).** Each oracle query draws from a fixed budget; guess-confirmations may cost more than property questions. This introduces a decision the toy does not currently have — the society must **triage**: which claims are worth verifying? Compare a naive "verify until the budget runs out" policy against a "spend verification only on the final guess / only on high-stakes claims" policy, and measure fail-open **per unit of verification spend**. This is the most open-ended arm (it requires designing a triage policy) and the closest to real Claude-Code economics (you cannot run the full suite on every intermediate step), so it sequences last.

**The redundancy / correlation study (rides on Arm 1).** Require `k` independent guess-confirmations before the gate signs. Under independent error, fail-open per wrong guess falls geometrically as `(1 − p)^k` — `k = 2` roughly squares the protection. Then dial correlation `ρ` from 0 to 1: as the `k` confirmations' errors become correlated, the `(1 − p)^k` benefit erodes, collapsing to no benefit at `ρ = 1` (re-asking the same flawed check gives the same wrong answer every time). **This is Part V's monoculture risk made measurable** — redundancy is a real mitigation exactly and only to the degree the checks are independent, and correlation is the mode by which the mitigation fails. It is the most valuable result in the whole design, because it is the one that transfers directly to the real system, where the checks would all be Claude-based and therefore correlated.

---

## Testing (protocol)

**Prerequisites.** B1 merged and adversarially verified (the oracle-checked guess exists, so `verify()` has a place to fail). The oracle-split, the belief-inert truth/answer/error-draw markers, and the post-hoc log classifier built and unit-tested against known logs.

**Per-game procedure.** (1) Register a true target from the pre-registered set. (2) Run the society as built, with the oracle answering through the error model at the configured `p`, error model, `k`, and `ρ`. (3) On termination, fold the log to the primary outcome and classify SIGN-CORRECT / SIGN-WRONG / ABSTAIN against the registered truth. (4) Retain the full log — it is the analysis substrate and the discovery substrate.

**The sweep grid.** For each error model {independent-uniform, systematic-per-question}: for each `p` in the sweep: `N` games at `k = 1`, difficulty-stratified. This is the primary-curve grid.

**The redundancy grid.** Fix a representative mid-sweep `p` (e.g. 0.8, where degradation is visible but the society still often reaches a guess). For each `k` in {1, 2, 3}: for each `ρ` in {0, 0.3, 0.6, 1.0}: `N` games. This isolates the `k`-benefit and its erosion with correlation without re-running the whole `p` sweep.

**The 6.7 scenarios (Arm 2).** Construct targeted games where a specific property question is (a) on the derivation path of the eventual guess, or (b) off it, and force the oracle to return a gap on that question. Assert: on-path gap → ABSTAIN, never SIGN; off-path gap → society routes around, outcome unaffected. Small `N`, high scrutiny — these are correctness assertions on a theorem, not statistics.

**The expensive policies (Arm 3).** At a fixed verification budget and a fixed mid-sweep `p`, run `N` games under {naive-verify-until-broke, triage-final-guess-only}. Compare fail-open per unit spend and abstention.

**Logging and reproducibility.** Every game logs its seed, true target, every oracle answer, and every error draw as belief-inert markers. Determinacy 4.1 plus the seeded oracle means the pre-registered runs are exactly reproducible; ship the seeds with the results.

---

## Analysis of results

**These are the analysis plan and the predicted interpretations — pre-registered, in the project's fail-closed idiom. No numbers below are measured; they name the shapes and the decision rules committed to before the run.** The actual curves are what running the experiment produces.

### Primary: the three-rate curves against reliability

For each error model, plot fail-open, abstention, and sign-correct against `p`. The decision turns on the **slope of the fail-open curve**:

- **Flat fail-open (near zero across the sweep), abstention rising to absorb the loss** → **H_graceful confirmed.** The architecture converts an unreliable check into honest silence. Sign-correct falls — fewer wins — but that is the acceptable cost; the system's job is to not *lie*, not to always *win*.
- **Fail-open rising linearly with `1 − p`, roughly one-for-one** → **H_catastrophic confirmed.** The gate is a pass-through; the guards do nothing. This would be the most important negative result the project could get, and it would say the whole approach needs a stronger `verify()` than a single fallible check can provide.
- **Fail-open rising but sub-linearly, with abstention taking most of the loss** → **the interesting middle,** and the real work: the *slope* is set by the guards (corroboration floor, Skeptic, redundant confirmation), and the follow-on studies attribute the slope to specific mechanisms.

Lead with the **systematic-per-question** curve. If independent-uniform looks graceful but systematic looks catastrophic, the honest headline is the systematic one — and that divergence is itself a finding about how much the architecture's apparent robustness depends on errors being conveniently uncorrelated.

### The operating envelope

Plot the joint `(fail-open, abstention)` pair as `p` sweeps. The shape distinguishes an architecture that *degrades honestly* (the point travels up the abstention axis, fail-open staying pinned low) from one that *degrades dishonestly* (the point travels out the fail-open axis). This is the deliverable that turns "it seems to work" into a stated **operating envelope**: *down to reliability `p*`, fail-open stays below `ε` and abstention below `δ`; past `p*`, one of them breaks* — the first genuinely measured claim about the system's reliability rather than an argued one.

### Redundancy and correlation (the transferable result)

Plot fail-open against `k` at fixed `p`, one line per correlation `ρ`.

- **Predicted at `ρ = 0`:** geometric fall in fail-open with `k` (`(1 − p)^k` per wrong guess) — redundant confirmation is a strong mitigation when checks are independent.
- **Predicted as `ρ → 1`:** the lines flatten; at `ρ = 1` the `k > 1` benefit vanishes entirely.

The interpretation is Part V made concrete and quantitative: **redundancy buys fail-closed safety in exact proportion to the independence of the checks.** If the run shows the `ρ = 1` line flat — redundancy useless under full correlation — that is the monoculture failure demonstrated on the bench, and it becomes the critical caution for the real system: **stacking correlated Claude-based checks does not buy the safety the geometric intuition promises.** This single plot is the strongest argument in the whole program for check *diversity* over check *quantity*.

### Partial oracle (the 6.7 correctness result)

Binary, per scenario: does an on-path gap always block, and does an off-path gap never block? A pass is a running-system confirmation of 6.7's gap-only, path-local fail-closed propagation — the first time the theorem is exercised rather than assumed. A failure in either direction is a discrepancy between the proof and the implementation and takes priority over everything else in the program, because it means the fold does not enforce what the calculus claims. The off-path case is the subtle one: if an off-path gap *does* block, the implementation is over-propagating gaps (fail-closed too aggressively — safe but useless), and the fix is in `maskedProject` / the derivation-dependency tracking, not the theorem.

### Expensive oracle (verification economics)

Compare fail-open per unit verification spend, naive vs triage. The predicted and desired result: **triage keeps fail-open low at far lower spend** — spending verification budget on the final guess (the critical check) rather than on every intermediate property question. If triage does *not* help — if fail-open per unit spend is the same either way — that says the intermediate checks carry as much fail-open risk as the final one, which would itself reshape where `verify()` needs to sit. This is the arm whose result most directly prices the real question: *given a limited testing budget, which checks are worth running?*

### Secondary: inspect the fail-open logs (the discovery loop)

Every SIGN-WRONG game is a full, replayable log. Read them. The two-factor decomposition says a fail-open is a wrong guess wrongly confirmed — but the *logs* say how the wrong guess arose (a corrupted-search drift like the apple case? a Skeptic that failed to refute a weak candidate? a corroboration floor cleared by two agents who both believed the same corrupted answer?). This is where the program has always found its new failure classes — the stale-hypothesis jam, the poison-by-precision, the unpinned referent all came from reading a single failing log, not from a summary statistic. Budget explicit time to read the fail-open logs, because the next finding is likely in them.

---

## Threats to validity (fair control on the experiment itself)

- **The society is Claude-based, and it reasons over the corrupted answers.** As `p` falls, contradictory oracle answers may confuse the agents' *own* reasoning, making it hard to cleanly separate "the architecture degraded" from "Claude got confused." The belief-inertness of answers is the mitigation — they do not move belief directly — and the logs let you inspect the mechanism, but this confound is real and should be named in any write-up, not buried.
- **Independent-uniform error flatters the architecture.** Stated above and worth repeating as a validity threat: a graceful result under independent error alone would be a weak claim, because independence is the easy case. The systematic and correlated models are what make the adversary strong enough for the measurement to mean something.
- **External validity is limited — a parametric simulated oracle is not a real test suite.** A real `verify()` fails in structured, content-dependent ways that a flip-with-probability-`p` model does not capture. The defense is not that the toy is realistic; it is that the toy **isolates the one variable** — check reliability — that the real system hopelessly confounds with everything else. The toy answers "how does *this architecture* respond to *check unreliability, holding all else fixed*," which no measurement on the real system can cleanly answer. Claim exactly that and no more.
- **Rare-event power.** In the graceful regime fail-open may be rare enough that the interesting quantity is its confidence interval, not its point estimate. Report intervals; do not over-read a zero-count cell as proof of zero risk.

---

## What this buys, on both tracks

**Research.** This is the measured evaluation that was the missing piece when we discussed whether any of this was thesis-grade. It converts the architecture's central claim from an argued property ("fail-closed by construction") into a measured operating envelope under the condition that actually stresses it ("here is how fail-open and abstention behave as the check degrades, under a realistic error model"). The redundancy/correlation plot is a self-contained, quotable finding.

**Productization.** Claude Code's `verify()` *is* a fallible, partial, expensive oracle — the three arms are a controlled study of the exact imperfection the real harness faces. The redundancy/correlation result informs the Claude-Code gate design most directly: it tells you whether stacking checks helps and warns that correlated (all-Claude) checks defeat the stacking. The expensive-oracle arm prices the triage question the real gate must answer. Running this in the toy **de-risks the gate that gives you daily leverage** before you build it there.

---

## The trigger (honest scoping)

By the program's own rule — *need announced by play, not the roadmap* — the go-signal for building this is not this document. It is **the first game, or the first Claude-Code session, that signs something wrong because its check was wrong.** That is the moment the confound stops being abstract and sits in front of you as a concrete failing log, and it is the right moment to stand up the fallible-oracle harness rather than before. Until then: designed, sequenced behind B1, and ready.

---

## Faithfulness to the algebra and calculus

Nothing in this experiment weakens the gate — it **measures** the gate under check failure. SIGN-WRONG is precisely the negation of the accept biconditional's soundness (`accept ⟺ corner=T ∧ cardinality=1 ∧ grade≥θ ∧ verify`, §4.4) once `verify()` can be wrong; the experiment quantifies how often a corrupted `verify()` breaks soundness and whether the surrounding guards suppress it. The partial arm is a running-system test of Theorem 6.7's gap-only, path-local propagation — the first exercise of the theorem outside the proof. The redundancy study adds no new sign path: it makes `verify()` require `k` ground-truth confirmations rather than one, which only *tightens* the biconditional's last conjunct. The oracle answers stay belief-inert throughout (they project to `Nil`; the poison, and now the measurement, live in the agents' downstream events), so the experiment respects the one fact that governs the whole design. The gate remains the sole signer, fail-closed; this program simply finds out what "fail-closed" is worth when the thing it closes against can lie.

*End — 2026-07-08.*
