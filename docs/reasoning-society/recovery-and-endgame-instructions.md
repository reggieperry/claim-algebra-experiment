# Instructions — recovery loop and endgame (recovering from a poisoned premise; closing the guess honestly)

**For Claude Code, continuing the Twenty Questions observability toy.**
*2026-07-07 · derived from the second "apple / not-a-living-organism" poisoned-premise log (70 events, the convergence monitor now firing at e-62) · builds on the fold, the belief-inert oracle answer, the shipped convergence monitor, and the workbench-imported sign floor · a set of ordered slices, cheapest-and-safest first · a HEALTH-and-RECOVERY pass, not a new reasoning engine*

---

## The failure this addresses (read first)

A game whose hidden answer was **apple** was lost at **e-7**, and no mechanism in the live game could recover it.

- e-4: the splitter asks "Is the hidden thing a living organism?"
- e-5: the human challenges the term (clarification engaged — correct).
- e-6: the splitter defines it precisely — *"metabolism, growth, reproduction, and response to environmental stimuli."*
- e-7: the human, thinking of an apple, answers **NO** (a picked apple is not actively metabolizing — defensible and sincere). That NO fenced off the entire fruit / produce region **where the answer lived.**
- e-8 → e-58: the society reasons impeccably through a linear material scan (metal → plastic → wood → glass → stone → rubber → fabric → liquid → food), reaching "food / edible" (e-57 YES) and narrowing toward "crystalline food, single piece, holdable" (e-60, e-65).
- e-62: the librarian's convergence monitor **fires** ("search not converging — 11 rounds") — the shipped feature working.
- e-63, e-69: the gate abstains "a lone-ish hypothesis backed by 1 of 2 needed."
- e-70: the gate abstains "inconclusive — no signature within the round budget." **The game ends with no guess.**

This is the apple bug in full: **a mis-grounded answer at the root invalidates the entire subtree beneath it**, and the society spends the whole game reasoning correctly inside a region that cannot contain the answer. Two things are notable and both are correct: the gate never signed a falsehood (honest abstention held), and the convergence monitor detected the non-convergence. What is missing is everything *after* detection — the recovery loop is open, and the honest abstention costs the society every poisoned game with no way to climb back out.

## The one fact that governs the whole design

**The oracle answer is belief-inert.** `GameCore.project` maps `Event.AnswerGiven => Nil` (`GameCore.scala:100`). The human's NO at e-7 never enters the belief fold directly. It flows into the transcript (`GameView.from`), is rendered into the agents' prompts, and the *agents* then emit the `Refute` / `Assert` events that actually fence the fruit region. So "the poisoning answer" is a belief-inert record; the poison lives in the agents' downstream events. Every recovery and endgame slice below is shaped by this: retracting an answer means dropping the agents' events reasoned from it, not the answer record itself.

Three standing disciplines constrain every slice, and none may be crossed:

- **Everything is a pure fold over the event log** — no mutable side state; belief is recomputed on read over a log prefix.
- **The librarian is non-generative** — it detects structure, never judges meaning.
- **The gate is fail-closed** — it may abstain freely and is never made to manufacture a signature.

---

## Rejected up front: lowering the sign floor

The obvious reading of e-63/e-69 is "the `MinCorroboration = 2` floor (`GameCore.scala:48`, imported from the workbench `Panel`) blocks the endgame — lower it to 1." **Do not.** On this exact log the driller's lone candidate was "crystalline food" (e-60), and the gate's message means it had already cleared corner=True ∧ cardinality=1 and was held back *only* by the backer floor. Lower the floor to 1 and the gate signs "crystalline food" — but the answer was apple. That turns this game's honest abstention into a confident-wrong signature, the exact failure the system exists to prevent. The floor is doing real work: it is the last guard between a poisoned game and a CWS. The endgame does not need a weaker internal quorum; it needs an **oracle-checked guess** (Slice 4), where the corroboration is ground truth rather than a second cheap agent. Leave `MinCorroboration` at 2; leave `MinRefuters` (`GameCore.scala:60`) at 2.

---

## Tier A — cheap, safe, ship first (no gate or fold changes)

### Slice A1 — honest convergence surfacing (one line)

**Gap.** The event-stream renderer leaks raw counters: `describeEvent.ts:134` emits `search not converging — ${roundsWithoutConsolidation} rounds, glut persisting ${glutPersistence}`. The flag fired here via the churn / budget signals, so `glutPersistence` is 0 and the human literally reads "glut persisting 0" — a meaningless counter leak. (The header badge, `HeaderBar.tsx:220`, already carries the right message — "you may want to reconsider an earlier answer" — so only the stream line is wrong.)

**Fix.** In the `convergence_warning` case of `describeEvent.ts`, render plain structural language and omit the glut clause when it is zero: `search not converging — ${rounds} rounds without a consolidating candidate`, appending `, one contested candidate held for ${glutPersistence} rounds` only when `glutPersistence > 0`.

**Discipline.** Structural counts only, no diagnosis of which answer is wrong; the display fold is untouched. Effort: trivial.

### Slice A2 — a best-effort tentative guess at budget end

**Gap.** `endInconclusive` (`LogActor.scala:388`) emits `GateAbstain("inconclusive …")` and returns `Outcome.Inconclusive` with no guess. The backer-sorted leading candidate is available (`GameView.hypotheses`, `GameView.scala:76`) but is discarded, so the game ends blank when it had a clear leading read.

**Fix.** At `endInconclusive`, surface the leading candidate (`GameView.hypotheses.headOption`) as a clearly-labeled tentative read — "leading guess: X (backed by n) — unconfirmed, not signed." Do **not** add a variant to `Outcome` (it stays the closed `Signed | Inconclusive`, `Protocol.scala:26-28`) so nothing can mistake it for a signature; carry it as display text only, never a `GateSign`.

**Discipline.** Fail-closed preserved — a tentative read is not a signature; the gate signs nothing new. The label must be unmistakable so it never reads as an answer. Effort: small.

### Slice A3 — a binary-question contract and game-framed definitions

**Gap A — question shape.** Nothing constrains a proposed question to be a single yes/no. The requirement lives only as prose in the trusted prompts (`AgentActor.scala:24`, `:45`); `AgentMove.question` checks only that the text is non-blank (`AgentMove.scala:62`). So the disjunctive "single piece OR a mixture?" (e-64) passed and drew an uninterpretable "YES" (e-65). Because the answer is belief-inert, this degrades the agents' search but *cannot* manufacture a wrong sign — a search-quality fix, not a correctness one.

**Gap B — definition framing (the poison-by-precision finding).** The clarification feature produced a biology-textbook definition of "living organism" (metabolism / reproduction, e-6), and that precision is exactly what drove the fatal NO. In Twenty-Questions terms an apple is obviously "alive / organic"; the precise definition was crisp about the wrong frame. **Definitional precision in the wrong frame is a poison, not a safeguard** — worth recording as a finding.

**Fix A.** Harden the propose contract in the system prompt (`AgentActor.scala:20-25` `outputContract`, `:44-49` splitter): one binary yes/no proposition, no disjunctive / compound / either-or questions, bisect one attribute per turn. Optionally add a weaker lexical, fail-closed guard in `AgentMove.question` that drops a multi-clause or either-or proposal (the propose becomes a no-post abstention — never a fabricated sign); keep it structural (surface tokens only) and test both the accept and the reject side.

**Fix B.** Reframe the definition-generation prompt (the splitter-defines path) to the game's discriminating purpose: define a term as it distinguishes candidate answers in this game — "is it, or was it recently, a living / growing thing, versus a manufactured object or mineral" — not by maximal technical precision.

**Discipline.** Question shape and definition framing are generation properties; they belong at the generation boundary and must never enter the librarian, the fold, or the gate. A prompt cannot be proven by the fold, so best-effort is the honest ceiling under a cheap model. Effort: small.

---

## Tier B — the real fixes (each `sbt check`-green then adversarially verified before commit)

### Slice B1 — the terminal guess-to-oracle move (the endgame fix)

**Gap.** The society has no name-and-confirm move. `AgentMove` is `Assert | Corroborate | Refute | Propose | Pass` (`AgentMove.scala:14-19`) — no `Guess` — and the oracle answers only property yes/no questions (`HumanMove` is `Answer | Challenge`, `Oracle.scala:17-19`). The only way to commit is `decide` signing on internal agent quorum, so even a win is agent-agreement, never ground-truth-confirmed, and a lone correct candidate can never close.

**Fix.** Pose the guess *only* for a candidate that already satisfies corner=T ∧ cardinality=1 — precisely the candidate for which `decide` returns `Abstain(Unconfirmed)` (the lone, unrefuted, rival-free hypothesis the gate flagged at e-63), never a plurality pick from an `Ambiguous`-blocked rival field, and never a glut. The posed candidate must be a real single slot `Answer`, not model free-text — tie it to A3's compound-question guard so a phrase the oracle cannot map to the secret is never posed. When such a candidate exists and the round budget is nearly spent or no useful property question remains, pose "Is it \<candidate\>?" to the oracle as a candidate-question, read purely from `GameCore.slot` / `GameView`. A **Yes** routes to `Outcome.Signed`; a **No** appends a `Refute` on that candidate and the search continues. This needs a candidate-question shape threaded through `Oracle`, `AgentMove` / `Event`, and `GameCore.project`.

**Discipline.** This is *not* a second sign path around the gate. The oracle Yes discharges only the missing no-lone-sign / verify() conjunct; corner=T ∧ cardinality=1 continue to hold from the evidence and grade≥θ is trivial, so all four conjuncts of the accept biconditional (`accept ⟺ corner=T ∧ cardinality=1 ∧ grade≥θ ∧ verify`, `claim-algebra.html` §4.4) are satisfied and the gate stays the sole signer. Its soundness rests on the oracle being the ground-truth holder who knows the hidden answer — so "Is it X?" is reliable disambiguation, unlike the fallible property-questions that caused the apple bug. The gate stays fail-closed — a No never manufactures a sign, and with no qualifying candidate the flow falls through to the existing `Inconclusive` path. The librarian is untouched (orchestration, not detection). This is the safe replacement for lowering the sign floor: the corroboration a lone candidate was missing is supplied by ground truth, not by a second cheap agent. Effort: medium.

### Slice B2 — the recovery loop: a prefix rewind (the apple-bug fix)

**This slice reopens a parked decision.** The memory note `twenty-questions-multislot-deferred` parked deeper reopen work on the reasoning that the convergence monitor is the right-scope structural response. This log is the first empirical evidence that **detection alone does not close the loop** — the monitor fired at e-62 and the game still died. By the project's own "need announced by play, not the roadmap" trigger, play is now announcing it. This is the narrower "let the human flip one poisoned answer," which is distinct from multi-slot referent decomposition (that stays parked). Confirm the reopen before building.

**Gap.** There is no way to retract, amend, or supersede a recorded answer. `AnswerGiven` is append-only (`Event.scala`) and belief-inert (`GameCore.scala:100`); the routes are `answer` / `challenge` / `start` / `reset` only (`SocietyRoutes.scala`); the only recovery is a whole-game New Game (`/start`) or Full Reset (`/reset`). The convergence flag is belief-inert too (`ConvergenceWarning => Nil`, `GameCore.scala:126`) and nothing consumes it, so the game runs on to a guess-less `Inconclusive`.

**Why a rewind is the minimal honest closure.** Belief is already a pure recompute over a log prefix — `slot` / `belief` / `decide(log, upTo)` take `log.take(upTo)` then fold (`GameCore.scala:135-166`); the frontend already scrubs to any prefix; and retirement masking already drops events and re-folds (`maskedProject`, `GameCore.scala:295`). Recovery therefore reduces to truncating the authoritative log to just before the poisoning `AnswerGiven` and re-forking. Because the answer is belief-inert, "retract the answer" means "drop the agents' events reasoned from it"; a prefix rewind drops them **wholesale**, so it needs no causal-dependency tracking and no new fold semantics.

**Fix.** Add `POST /rewind {toSeq}` (a `jsonOf` decoder beside the existing two, `SocietyRoutes.scala:32-34`), routed to a new `GameSupervisor.rewindTo(seq)` modeled on `newGame` (`GameSupervisor.scala:50`): run it **under the same mutex** (`GameSupervisor.scala:10`) so two requests cannot stack games, go through the cancel → (harvest definitions) → re-fork spine, and seed the fresh game with the **truncated prefix** instead of `clearWorking`. The rewind must go through this supervisor spine — the authoritative log is the single-writer `LogActor`'s state — never by mutating the routes' SSE-mirror `logRef`. Frontend: a "rewind to here" control on `AnswerGiven` rows (the scrub-plus-button shape already exists), and, when the convergence flag fires, surface the recent answers by position so the human can pick which to revisit.

**Why no culprit-scoring.** Pointing at the specific poisoning answer is barred: answers are belief-inert (they never enter the belief history the monitor folds over) and "which answer pruned the most" is a semantic judgment the non-generative librarian cannot make. The honest surface is "here are your early yes/nos — reconsider one," not "e-7 is the culprit." The human holds the ground truth and makes the call.

**The synergy to note.** After a rewind to before e-7 the human re-answers "living organism?" — and with the game-framed definition of Slice A3 they answer YES (an apple is a growing thing), reopening the fruit region. A3 prevents the re-poison; B2 enables the recovery. Build A3 first.

**The surgical alternative (defer).** Add a belief-inert `Event.AnswerRetracted(qid)` — a **retraction, not a supersession**: it projects to `Nil` (like `AnswerGiven`, since the answer contributed no belief to withdraw) and drives a per-round event **mask**, extending `maskedProject` to drop the agents' `Assert` / `Corroborate` / `Refute` events opened in the retracted answer's round. Do **not** name it "superseded" or route it through `supersede` (¬) or `Evidence.WithdrawnToken`: `supersede` would move the fruit-region con onto the con-channel and glut it forever (the rehabilitation asymmetry), and `WithdrawnToken` is a belief-moving Evidence keyed to one assertion's token, not an event-dropping tool — one round is many per-event tokens. Compose the answer-mask *before* retirement so `retiredCandidates` recomputes over the already-masked prefix (else a candidate retired only by an excised refutation inherits a spurious retirement, breaking invariant 5(i)). It is strictly more work because it must define which downstream events "belong to" the retracted answer — the causal-dependency question the current model does not track — and, where a whole-slot `Strike` sits in the retained or excised segment, mid-log excision is order-sensitive (full confluence is a non-theorem, Remark 5.4), which is a further reason to prefer the wholesale rewind (a prefix truncation, hence a deterministic re-fold by Determinacy 4.1). Build it only if the wholesale rewind proves too blunt in play. Effort: small-medium (rewind); large (surgical, deferred).

### Slice B3 — information-gain search

**Gap.** There is no space model or information-gain anywhere; "halve the space" is only prose in the splitter prompt (`AgentActor.scala:45`), so a cheap model degrades to the linear material scan the log shows (metal → plastic → wood → …), burning roughly eight of twelve rounds before a concrete candidate is even asserted.

**Fix.** Add a belief-inert candidate-enumeration event (shaped like `DefinitionGiven` — it must project to `Nil`, never fold into `Testimony`, never touch the gate): an agent proposes and refines the explicit set of remaining candidate answers consistent with the transcript. Surface that set as a new `GameView` field rendered into the splitter's prompt, and instruct the splitter to choose the yes/no question that most evenly partitions it. A cheap prompt-only palliative (brainstorm roughly eight candidates spanning materials and non-material categories, then bisect) reduces the linear-scan burn without new events but gives no inspectable information-gain fold.

**Discipline.** Candidate-space and information-gain reasoning is generative — keep it strictly on the agent side, never in the librarian (`Convergence`) or the gate. The new event's `project` case is `=> Nil` (the exhaustive match makes this compile-enforced), and the new `GameView` candidate-set field is display/prompt-only: it must never be read by `slot` / `belief` / `decide` / `Gate` or by `Convergence` — the librarian reads `GameView.hypotheses` on a path independent of `project`, so projecting the event to `Nil` guards the fold but *not* the librarian's metamorphic invariance. Listing a candidate is not asserting it — belief moves only through a real `Assert` / `Corroborate` / `Refute`. Note this fix is upstream of the loss only for *winnable* games: on a poisoned premise, a faster search reaches the wrong region faster. Effort: medium.

---

## What NOT to build

- Do **not** lower `MinCorroboration` — on this log it signs "crystalline food" over the true "apple." The floor is a guard, not the bug.
- Do **not** make the convergence flag change the gate or manufacture a sign — it stays a request for help.
- Do **not** have the librarian name the culprit answer or score blast radius — that is the forbidden semantic judgment. Surface the answers by position; let the human diagnose.
- Do **not** add a best-guess variant to `Outcome` that could read as a signature — keep the enum closed and carry the tentative read as display text.
- Do **not** attempt semantic dedup or semantic similarity of questions — lexical / structural only.
- Do **not** add multi-slot referent decomposition — out of scope (finding 7; the convergence monitor is the right-scope structural response). The rewind is the narrower "flip one poisoned answer," and is a different thing.
- Do **not** build the surgical per-answer **retraction** yet — the wholesale rewind first. And do not name it "supersession" or route it through `supersede` / `WithdrawnToken` on the answer: the answer is belief-inert, and `supersede` (¬) would move the fruit-region con onto the con-channel and glut it forever.

## Acceptance

- **A1** — the event-stream line reads "search not converging — N rounds without a consolidating candidate" and shows no "glut persisting 0"; the glut clause appears only when the count is positive.
- **A2** — a budget-exhausted game ends with a clearly-labeled unconfirmed leading guess; `Outcome` is unchanged; no `GateSign` is emitted.
- **A3** — a proposed disjunctive question is rejected or reshaped before it reaches the oracle (test both accept and reject sides); a challenged term is defined in game-frame, and a replay of the apple game shows a definition that does not force the fatal NO.
- **B1** — a lone, unrefuted, rival-free candidate (a `decide` `Abstain(Unconfirmed)`) near budget end is posed to the oracle; a Yes signs (`Outcome.Signed`), a No refutes and continues; with no qualifying candidate the flow still reaches `Inconclusive`. An `Ambiguous` belief (two or more live rival positives) is never posed — no plurality-leader bypass — and the posed candidate is always a real single slot `Answer`. The gate never signs on a No.
- **B2** — `POST /rewind {toSeq}` truncates the authoritative log by the same positional-prefix semantics `slot` / `belief` / `decide` already use (`log.take(toSeq)`), so the re-fold is exactly `belief(log.take(toSeq))` (Determinacy 4.1); the fruit-region refutations are gone and belief matches the pre-answer state; the mutex prevents stacked games; the SSE mirror reflects the rewound log; replay shows the truncation point. On the apple log, a rewind to before e-7 followed by a YES reopens the fruit region.
- **B3** — the enumeration event projects to `Nil`, and the new `GameView` candidate-set field changes no `slot` / `decide` output on any log (a test pins this); a metamorphic test permutes and renames the enumerated candidate set while holding the `Assert` / `Corroborate` / `Refute` structure fixed and confirms `Convergence.flag`'s fire/no-fire and the `Warning` counts are unchanged (project-to-`Nil` guards the fold, not the librarian, so this test is the real guard); the splitter, given an explicit candidate set, asks a partitioning question rather than the next material in a list.

## Faithfulness to the algebra and calculus

Checked against `claim-algebra.html` §§3–5 (the bilattice, the fail-closed annihilator, the §4.4 accept biconditional) and `claim-calculus.html` §§4–6 (determinacy 4.1 / normalization 4.2, confluence-on-assertions 5.3, non-fabrication 6.2), plus the proven code suites (`LedgerLawsSuite`, `FoldSemanticsSuite`, `ResolveSuite`), through five adversarial lenses. **No slice breaks a law or theorem.** The wholesale rewind (B2) is safe by Determinacy — it re-evaluates the same deterministic fold on a positional prefix, `belief(log.take(toSeq))`, adding no new semantics — and non-fabrication holds a fortiori because truncation only removes events. The corrections folded in above: B1 poses the guess only on a corner=T ∧ cardinality=1 candidate, so the oracle Yes discharges the one missing conjunct without opening a second sign path; B3 fences the candidate-set field to prompt-only and pins the librarian's metamorphic invariance with a test (project-to-`Nil` guards the fold, not the librarian); and the deferred surgical retraction is a `maskedProject`-style event mask driven by a belief-inert `AnswerRetracted`, never `supersede` / `WithdrawnToken` on the answer (which would glut the reopened region forever).

## First move

Ship **A1** and **A2** together (trivial and small, no gate or fold change), then **A3** (prompt-side). Each slice `sbt check`-green; A2 and A3 adversarially verified because they touch the abstention surface and the generation contract. Then confirm the parked-decision reopen and build **B1** and **B2** — both touch the sign path and the recovery spine, so each gets an adversarial-verify (reachable fail-open: a No that signs, a rewind that leaves poison behind, a stacked game) before commit. Hold **B3** until the winnable-game search quality is the bottleneck rather than the poisoned-premise recovery.

*End of instructions — 2026-07-07.*
