# The fallible-oracle experiment — build plan

**The code-grounded HOW for `fallible-oracle-experiment-design.md`.** That doc is the *why* and the *what* (the falsifiable pair, the arms, the analysis plan); this is the *how*, sliced and anchored to the actual seams in `reasoning-society/backend/src/main/scala/claimalgebra/society/`. Written 2026-07-08 after a four-reader seam map of the toy; B1 (the oracle-checked guess — the experiment's hard prerequisite) shipped this session, so the main arm now has a place to measure.

## What the seam map established (three findings that shape the build)

1. **The oracle-split is trivial — no core change.** `Oracle` is a one-method trait — `def respond(question: Question): IO[HumanMove]` (`Oracle.scala:28`) — injected as a plain by-value parameter into `Society.play` (`Society.scala:67`) and reached only as `deps.oracle.respond(...)`. The experimenter/oracle-channel split lives *entirely* behind a new `Oracle` implementation passed to `play`. `Question` already carries `(id: QuestionId, text: String)`, so a systematic-per-question error model has the `(question, truth)` key it needs at answer time. Both property questions *and* B1 guesses (`Question("guess-<c>", "Is it <c>?")`) flow through the one `respond`.

2. **The measurement is nearly free and leak-proof by construction.** `GameCore.project` is an **exhaustive** match over the closed `Event` enum (no `case _`), so a new belief-inert marker is compiler-forced to `=> Nil` under `-Werror` (same for `Event.agentId => None`). And the agent-facing view `GameView.from` (`GameView.scala:57`) is a **positive whitelist** with a `case _ => acc` fallthrough — `transcript ← AnswerGiven/QuestionAsked`, `hypotheses ← Assert/Corroborate`, `definitions ← DefinitionGiven/Remembered` — so a new `TargetRegistered` marker matches nothing and **cannot reach the agent prompt**. The true target stays adjudication-only with no filter to write. Classification is then a pure `collect` fold over the collected `Vector[Event]`.

3. **The fail-open locus is confirmed at exactly one place.** The gate signs at `onGuessAnswered` (`LogActor.scala:290`) iff `answer == Yes ∧ GameCore.decide == Sign(winner) ∧ winner == candidate`. `decide` (`GameCore.scala:159`) signs iff `Gate.accept` (corner=True ∧ cardinality=1 on the live masked fold — θ/ν/verify all wired off) **and** `backers >= MinCorroboration(2) || oracleConfirmed(prefix).contains(winner)`. The nominal `verify` conjunct is trivially satisfied (`trusting`); the real verification authority is the **`oracleConfirmed` disjunct** — the oracle's ground-truth `Yes` substitutes for the missing second backer and lets a lone candidate sign. **A corrupted `Yes` to a wrong guess is the one and only fail-open.** A corrupted *property* answer projects to `Nil` (`AnswerGiven`, belief-inert) and only misleads *search*.

**Net:** Arm 1 + the whole measurement need **no fail-closed-core change** — a new `Oracle` impl, two belief-inert markers, a fold classifier, and a headless runner. Only the redundancy study (Slice 4) touches the sign path.

## The slices

### Slice 0 — measurement apparatus (additive, no behavior change)

- Add belief-inert `Event` variants: `TargetRegistered(seq, timestamp, target: Answer)` (the sealed truth, adjudication-only) and — optional, deferrable — `OracleErrorDraw(seq, timestamp, questionId, corrupted: Boolean)` for per-answer inspection. Wire each into the compiler-forced sites: `GameCore.project => Nil`, `Event.agentId => None`. Add **no** case to `GameView.from` / `Definitions.from` (the fallthrough is the non-leakage guarantee).
- Seed the truth via the **existing `initial: LogState` param** on `Society.play` (added for B2's rewind): start each game with `TargetRegistered` already at `seq 1`. No new wiring in the actor.
- A pure classifier: `classify(events): PrimaryOutcome ∈ {SignCorrect, SignWrong, Abstain}` comparing the terminal sign (`Event.GateSign` / `Outcome.Signed`) to the registered `TargetRegistered.target`.
- **Verify:** unit-test `classify` against hand-built logs (all three outcomes); a test that a seeded `TargetRegistered` **never** appears in `GameView.render` (the non-leak property — safety-relevant); reproducibility of the fold.

### Slice 1 — `ExperimentOracle` + error models

- `ExperimentOracle` — a new `Oracle` impl (mirror `Oracle.scripted`'s shape: `IO[Oracle]`, a `Ref` holding a seeded PRNG + a draw record, fail-closed default), whose `respond` (a) computes the *truthful* answer to `question` given the sealed target, (b) applies an `ErrorModel`, (c) records the draw, (d) returns the (possibly flipped/gapped) `HumanMove`. Unlike `scripted` (positional cursor, ignores the question), it **branches on `question`**.
- `ErrorModel` — a pure seam `(question, truthfulAnswer, draw) => OracleAnswer`, with impls: **independent-uniform** (i.i.d. flip with prob `1−p`), **systematic-per-question** (a fixed, deterministic wrong-subset keyed on `question.text`/`id` — re-asking the same question yields the same answer), and a correlated variant (feeds Slice 4). `Unknown`-gap for Arm 2.
- The *truthful-answer* function is the one genuinely new piece of domain logic: given a property question ("Is it a living organism?", "Is it a fruit?") and the target ("apple"), what is the correct yes/no? This is the experimenter's ground-truth oracle — pre-registered per (question-space, target) or itself a (held-fixed) model call; **a design decision, see below.**
- **Verify:** property tests — `p=1.0` ⇒ the truthful oracle (SignCorrect achievable, zero corruption); flip rate matches `1−p`; systematic re-asks are stable; a seeded run is bit-reproducible.

### Slice 2 — headless batch runner + grader + trial store (the genuinely new infra)

The toy has **no** batch runner today — only `RunServer` (SSE) and `RunSociety` (console single-game). But `Society.play` is a fully self-contained headless seam (the test suites already batch it), and the root-module experiment gives templates.

- `runOneGame(cell, seed)` = build `ExperimentOracle` + `collectingSink` (`Ref[Vector[Event]]`) + seeded `initial` LogState (`TargetRegistered`) → `Society.play(liveLlm, oracle, sink, config, initial)` → `classify` → `GameRecord`.
- `GameRecord(reliability p, errorModel, k, ρ, difficultyTier, trueTarget, signed: Option[Answer], outcome, seed)`.
- Batch driver: `(1..N).parTraverseN(k_conc)(runOneGame)` — **bounded** fan-out (each game already fans 3 Haiku calls/round; a game is ~tens of calls; `parTraverse` unbounded would hit rate limits). Share one `AnthropicLlmCall` client; fresh oracle + sink per game.
- **Lift `Rate.scala`** (pure Wilson CI, `count/n`, `.ci95`, `.strictlyBelow`) from the root experiment module into the `claimAlgebra` library (already domain-neutral) so both experiments share one implementation. Mirror — cannot import, `reasoningSociety` doesn't depend on the root module — `TrialStore` (in-memory `Ref[Vector[GameRecord]]`), `Report.summarize` (`groupBy(cell) → {failOpen, abstain, signCorrect} rates + CIs`), and the `Harness`/`RunExperiment` matrix-runner + `IOApp` shapes as `RunOracleSweep`.
- **Verify:** run the pipeline **hermetically first** — scripted agents, `p=1.0` and `p=0.5` — to validate classification and rate computation without spending a cent, then a tiny live smoke. This is all additive; `sbt check`-green is the bar (no committee — no core touch).

### Slice 3 — Arm 1 sweep (a *run*, not code) — the primary curves

`{independent-uniform, systematic} × p-sweep {1.0…0.6} × N × difficulty-stratified`, live Haiku → the three-rate curves, the operating envelope, the two-factor decomposition. **Expensive** — gated by the trigger and a cost check.

### Slice 4 — redundancy / correlation study — ⚠️ THE ONE CORE-TOUCHING SLICE

**BUILT + adversarially-verified MERGE_SAFE (2026-07-08, 231 tests).** Byte-identical at k=1; k>1 reaches nothing shipped. The one-line summary: `decide`'s floor disjunct became `oracleConfirmations >= k`, the LogActor pose guard became a per-candidate budget (re-pose via the existing give-up ladder), `CorrelatedConfirmations` realizes ρ, guess-truth is structural, and `SweepCell(k,ρ)`/`GameRecord.signPath` carry the study.

**The (k, ρ) fail-open CURVE — HERMETIC, end to end.** The live toy mostly abstains, so the "full sweep" is hermetic rather than billed: a scripted lone-"apple" cohort always reaches the give-up guess, target="dog" so every sign is an oracle-confirmed fail-open, answered through `CorrelatedConfirmations`. Swept `k∈{1,2,3} × ρ∈{0,0.5,1}`, N=800/cell, through the REAL society/gate/re-pose loop (`RunOracleSweep` default; `renderCorrelation` counts only `OracleConfirmed` signs via `signPath`):

```
  k   rho      N  FAIL-OPEN (oracle-confirmed)   (1-p)^k   (1-p)
  1  0.00/0.50/1.00        0.32 (flat)             0.300   0.300   ← no redundancy
  2  0.00                  0.093 [0.07-0.11]        0.090   0.300   ← independent: redundancy pays
  2  1.00                  0.320 [0.29-0.35]        0.090   0.300   ← correlated: buys nothing
  3  0.00                  0.024 [0.02-0.04]        0.027   0.300   ← ~13× suppression
  3  1.00                  0.320 [0.29-0.35]        0.027   0.300   ← monoculture: flat at (1-p)
```

Along ρ=0 the fail-open falls geometrically with k; along ρ=1 it is flat at (1-p) — three correlated confirmations are no safer than one. **Epistemic adversarial-verify = GENUINE-AS-FRAMED, one overclaim softened:** ρ is an *assumed, swept* parameter, so this makes the Part-V monoculture failure mode CONCRETE and validates it end to end *as a function of a given correlation* — it does NOT measure real all-Claude-check correlation, and the curve equals a closed form a pure test already pins, so its value is end-to-end integration confidence, not a numerical discovery. Not a finding-7 rig (no control, no arm comparison). A real *billed* sweep remains a separate trigger-gated run and would only add live confounds (abstention), not tighten this curve.

The only part that modifies the fail-closed sign path. **Committee-reviewed 2026-07-08 → PROCEED WITH CHANGES; the three critical code-facts were then verified directly against source.** Design of record (revised from the seam-map sketch, which was wrong on two points):

- **(A) GameCore — pure, one line + a fold.** Add `oracleConfirmations(log, c): Int` (count of `GuessAnswered(c, Yes)`); thread `k: Int` **from `SocietyConfig`, DEFAULT 1** (a global `K=2` constant would break `GuessGateSuite`/`SocietyGameSuite` *and* silently change the shipped live game — at k=2 the once-per-candidate live guess would never sign a lone candidate). Change the disjunct at `GameCore.scala:184` to `backers >= MinCorroboration || oracleConfirmations(prefix, winner) >= k`. **k gates ONLY the oracle-confirmed disjunct** (Q4); the 2-distinct-backer path is untouched. Recomputed-on-read (no stored counter → B2-rewind-safe: a resumed prefix has `oracleConfirmations = 0`).
- **(B) LogActor — smaller than the sketch; no bespoke loop.** `giveUpOrGuess` already poses the clean lone winner. The ONLY change: relax `alreadyGuessed` from "posed at all" to a **per-candidate pose budget — `count of GuessAnswered(c, _) < k`, counting ALL answers, not just Yes** (else an all-`Unknown` candidate loops forever). A `Yes` short of k falls through `onGuessAnswered`'s `case _ => giveUpOrGuess` and re-poses via the existing path. Re-poses stay **strictly sequential** (one guess in flight per game — the invariant that precludes a `RemoteOracle` double-complete). Stop re-posing after the first **non-`Yes`** (a `No` masks; any non-`Yes` already makes k-`Yes` impossible in budget — fail-closed). Signing stays gated exactly as today (`decide=Sign ∧ winner==candidate`, `Gate.accept` re-read on the live fold each confirmation). **No key de-collision** — verified: `Mailbox.offer` does not dedup on `MessageId` (durable-tier only), so confirmations 2..K are not swallowed; and de-colliding the *QuestionId* would collapse the ρ knob (the correlated model needs a candidate-stable qid). Keep `guess-<c>` candidate-stable.
- **(C) A dedicated `CorrelatedConfirmations(p, ρ, seed)` ErrorModel** (do NOT ride ρ on varied question framing — that confounds the error channel with the truth channel, since the truth oracle keys on `question.text`). Derive a candidate-stable common-mode latent from `(question.id, seed)`; use the fresh per-pose `draw` as the independent component; deliver the common bit with probability ρ else the independent bit, each `Bernoulli(1−p)`. ρ=0 → false-confirm ≈ `(1−p)^k`; ρ=1 → ≈ `(1−p)` (the monoculture failure). Extend `SweepCell`/`GameRecord` with `(k, ρ)` **and a sign-disjunct attribution field** (k affects only the oracle disjunct; 2-backer wrong-signs are k-invariant and must be isolated in analysis).
- **Also (B-3, closes a real confound): compute guess-confirmation truth STRUCTURALLY.** For a `guess-<c>` question the truth is `Yes iff c == target` — short-circuit the `TruthOracle` (both table and model paths) rather than asking a model a truth the harness already owns (that model call is exactly what noised the observed `crystal_vase` fail-open).
- **Tests (red-first → adversarial-verify MERGE_SAFE):** k=1 byte-equivalence to HEAD (existing suite re-expressed, not deleted); k=2 needs two genuine `Yes`; abort-between-confirmations (`Yes` then `Refute` → Blocked); `No`/`Unknown` terminates in budget; rewind drops guesses (`oracleConfirmations = 0` on a resumed prefix); one-guess-in-flight; ρ endpoints pinned as properties; an analytic cross-check asserting the live curve matches `(1−p)^k` within its Wilson CI.
- **Rejected alternative (Design 2):** realize the k correlated confirmations inside one oracle round-trip, leaving the sign path at k=1 — same numbers, zero sign-path surgery. Rejected because it measures the *error model*, not the *architecture* (the study's whole point is that the GATE requires k independent confirmations); its only real advantage (no live-game leakage) dissolves once k is config-threaded with default 1. Adopt its mechanism ONLY as the analytic cross-check oracle.

### Slices 5–6 — Arm 2 (Partial / 6.7) and Arm 3 (Expensive)

- **Arm 2:** `Unknown`-gap on a chosen question. `Unknown` already exists and is belief-inert (neither signs nor masks). The correctness assertions (on-path gap → ABSTAIN; off-path gap → route around) need the *path-local* derivation-dependency notion — the interesting question is whether the toy already routes around an off-path gap or over-propagates it. Small N, high scrutiny — correctness assertions on Theorem 6.7's first running-system exercise.
- **Arm 3:** verification budgeting + a triage policy (verify only the final guess vs. everything), fail-open per unit spend. Most open-ended (requires designing the triage policy); sequences last.

## Decisions to settle

1. **Which answers the error model corrupts** — property answers (indirect, drives the "wrong guess posed" factor) and/or the guess `Yes`/`No` (the direct fail-open). The centerpiece is the guess-confirmation; the two-factor decomposition wants both. Make it a per-run knob.
2. **The experimenter's truthful-answer function** — how does the harness know the correct yes/no for an arbitrary agent-generated property question about the target? Options: a pre-registered target set with a fixed property table (deterministic, closed question-space — cleanest, but constrains agent questions), or a held-fixed truthful model call (open question-space, but adds a second model in the loop). This is the sharpest design question and gates the target-set design.
3. **Cost.** Genuinely expensive — thousands of live-Haiku games across cells, ~tens of calls each. A rough $ estimate before committing to full `N`. Hermetic pipeline validation is free; the curves are not.
4. **The trigger.** The design doc's honest-scoping: build the *runs* when play announces it (the first wrong-because-check-wrong sign). Slices 0–2 are cheap, additive, zero-core-change — buildable **now** to be *ready*. Slices 3+ (runs) and Slice 4 (core change) are what the trigger gates.

## Sequencing and gates

- **Now (be ready):** Slices 0–2 — apparatus + oracle + runner + grader. Additive, `sbt check`-green, no core touch.
- **Slice 4 touches the fail-closed sign locus** → committee-review → build TDD → adversarial-verify (MERGE_SAFE, no new fail-open) before it merges.
- **Slices 3, 5, 6 are runs** — gated by the trigger and the cost check.

*Companion to `fallible-oracle-experiment-design.md`. This is the as-designed build plan; the seam anchors are from the 2026-07-08 four-reader map and should be re-verified against source at build time (do not compose identifiers from this doc).*
