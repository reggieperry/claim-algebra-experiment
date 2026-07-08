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
- **Lift `Rate.scala`** (pure Wilson CI, `count/n`, `.ci95`, `.strictlyBelow`) from `src/main/scala/claimalgebra/experiment/` into the `claimAlgebra` library (already domain-neutral) so both experiments share one implementation. Mirror — cannot import, `reasoningSociety` doesn't depend on the root module — `TrialStore` (in-memory `Ref[Vector[GameRecord]]`), `Report.summarize` (`groupBy(cell) → {failOpen, abstain, signCorrect} rates + CIs`), and the `Harness`/`RunExperiment` matrix-runner + `IOApp` shapes as `RunOracleSweep`.
- **Verify:** run the pipeline **hermetically first** — scripted agents, `p=1.0` and `p=0.5` — to validate classification and rate computation without spending a cent, then a tiny live smoke. This is all additive; `sbt check`-green is the bar (no committee — no core touch).

### Slice 3 — Arm 1 sweep (a *run*, not code) — the primary curves

`{independent-uniform, systematic} × p-sweep {1.0…0.6} × N × difficulty-stratified`, live Haiku → the three-rate curves, the operating envelope, the two-factor decomposition. **Expensive** — gated by the trigger and a cost check.

### Slice 4 — redundancy / correlation study — ⚠️ THE ONE CORE-TOUCHING SLICE

The only part that modifies the fail-closed sign path — **committee-review then adversarial-verify before building**, same discipline as B1/B2.
- *GameCore (small, pure):* swap `oracleConfirmed(prefix).contains(winner)` for `oracleConfirmations(prefix, winner) >= K` (a fold-count) with a `K` constant symmetric to `MinCorroboration`.
- *LogActor (the substance):* turn the single-shot guess into a **bounded re-pose loop** — re-pose on a `Yes`-short-of-`K` (only while the candidate stays clean-lone), relax `alreadyGuessed` (`LogActor.scala:527`) to a per-candidate pose budget, and **de-collide the message/question keys per pose** (`guessed-<c>` / `guess-<c>` — else idempotent delivery swallows confirmations 2..K). Signing stays **only** via `decide=Sign ∧ winner==candidate`; the two-gated-sign-sites safety spine is preserved, and `decide` re-reads `Gate.accept` on the live fold at every confirmation (a glut between confirmations aborts the quorum).
- Correlation `ρ` rides the `ErrorModel` (the k re-poses draw errors with correlation `ρ`: `ρ=0` independent → `(1−p)^k`; `ρ=1` same draw → no benefit). This is the crown-jewel result — monoculture made measurable.

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
