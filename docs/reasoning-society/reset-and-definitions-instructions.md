# Instructions — two-tier reset & definitions as the first memory

**For Claude Code, continuing the Twenty Questions observability toy.**
*2026-07-07 · builds on the existing fold/event-log architecture and the clarification feature*

---

## Why this is more than a button (read first)

You added a **Restart Game** button, and it raised the question: should restart clear the agreed definitions, or keep them? That question is not a UI detail — it is the **memory architecture surfacing as a reset decision.** The line between "what a restart clears" and "what survives a restart" is exactly the line between **working memory** (this game's state) and **persistent memory** (what carries across games). So building the two-tier reset correctly *is* building the first real memory tier — and definitions are the right first payload because they are atomic, stable, and low-risk (see §4).

The whole point: **a game restart clears the working memory but preserves the persistent memory; a full reset clears both.** Get that distinction into the data model, not just the buttons.

---

## 1. The core distinction: two scopes of state

Split all system state into two scopes:

- **Working memory (game-scoped).** Everything about the *current* game: the target being guessed, the hypotheses/candidates, the evidence (the human's yes/no answers to *this* game's questions), the belief state, the current question. This is what a new game should wipe — a fresh guessing session starts with a blank slate of hypotheses and answers.
- **Persistent memory (session-scoped, survives restarts).** Things established that are *true across games*. Right now this is exactly one thing: **agreed definitions** ("in this system, 'alive' means [living organism, currently alive — a picked fruit is no]"). These should NOT be wiped by starting a new game, because re-litigating "what does alive mean?" every game is pure waste.

This maps directly onto the architecture's memory tiers: working memory = the active task's ledger; persistent memory = the beginning of semantic memory (grounded shared vocabulary).

## 2. Two buttons, two behaviors

- **New Game** (the restart you added): clears **working memory only**. New target, empty hypotheses, empty evidence, fresh belief state — but **definitions persist** and are available to agents from the first question of the new game. The librarian/retrieval path (§3) surfaces them.
- **Full Reset** (new): clears **both** working and persistent memory. Definitions gone, back to a truly blank system. This is the "start completely over" / "clear everything I've taught it" button.

Label them so the distinction is obvious to the human (e.g. "New Game" vs "Full Reset" — and consider a one-line tooltip on Full Reset: "also clears learned definitions"). A brief confirm on Full Reset is reasonable since it discards accumulated definitions; New Game should be instant (it is the common action).

## 3. How to implement it on the existing fold (do NOT bolt on side state)

This must stay consistent with the event-log/pure-fold foundation (§1 of the build brief). Do not introduce mutable state that lives outside the log. Two clean options — pick whichever fits the current code, but keep the fold pure:

**Preferred: scope events, and fold within scope.**
- Every event already goes in the log. Add a **scope tag** to events: `game_id` (which game it belongs to) and a scope kind (`working` vs `persistent`).
- **Definition events are tagged `persistent`** (not tied to a single `game_id`); evidence/hypothesis/answer events are tagged `working` with the current `game_id`.
- **New Game** = start a new `game_id`. The working-memory fold computes belief state from `working` events *of the current game_id only*. Persistent events (definitions) are always in scope regardless of game_id. Nothing is deleted — a new game just changes which working events the fold reads, and the definitions carry over because they were never game-scoped. (Bonus: this keeps prior games in the log, so you could later review or replay them.)
- **Full Reset** = clear the log entirely (or start a fresh log/session), discarding persistent events too.

This is the cleanest option because it preserves the "never delete, derive everything from the log" discipline and even keeps old games around for free.

**Acceptable if simpler given current code: two logs / two folds.**
- A **persistent event log** (definitions) and a **working event log** (current game). The belief state folds the working log; definitions fold the persistent log; the UI reads both.
- **New Game** clears the working log, leaves the persistent log. **Full Reset** clears both.
- Slightly less elegant (two logs), but still keeps each fold pure. Fine if it's less surgery on what you have.

Either way: **replay still works** — replaying is folding the relevant events up to the playhead, and definitions being persistent just means they are in scope across the whole replay. Make sure the transport/playhead behaves sensibly across a New Game boundary (simplest: the playhead scrubs within the *current* game's working events, with persistent definitions always applied; decide and note the behavior).

## 4. Definitions as real claims (keep the discipline)

Definitions must remain **claims in the ledger with provenance**, not ephemeral chat or a plain key-value dict off to the side. This is what makes them the first real memory tier rather than a hack:

- A definition claim records: the term, its agreed meaning, and **provenance** — which game / which clarification exchange established it, and (if the asking agent gave it) which agent.
- Because it has provenance and is a claim, it is **challengeable and supersedable** in a future game. If a new game's human means something different by "alive," the clarification feature (already built) lets them **challenge the retrieved definition**, and the new meaning **supersedes** the old one (retained as trace, per the algebra — not deleted). The human challenging a *remembered* definition is the retraction/supersession mechanism in its gentlest form.
- This is the safety valve for the memory-specific risk (§5). Build the challenge-the-retrieved-definition path even if it rarely fires — it is what keeps a stored definition from silently mis-grounding a game that meant something else.

## 5. The one caution — institutional false memory, in miniature

The moment definitions persist, you inherit (in tiny form) the architecture's institutional-false-memory risk: a stored definition can **mis-ground a future game** with the authority of "we already established this." Defenses, all cheap here:

- **Provenance + scope**: a definition knows where it came from; it is not an anonymous global truth.
- **Challengeable/supersedable**: §4 — the human can override a retrieved definition, and the override supersedes with a retained trace.
- **Visible**: the human should be able to *see* the current persistent definitions (a small memory panel or list), so stored vocabulary is never invisible. Seeing "here is what I currently think these terms mean" is the audit surface, and it is the same instrument-that-shows-its-work principle as the rest of the UI.

## 6. What NOT to build yet (scope discipline)

Do **not** add general cross-game memory in this pass — no episodic "remember what happened in past games," no validated-relationships tier, no cross-game learning. Definitions are the *only* persistent payload for now, deliberately, because they are atomic, stable, clearly useful, and low-risk — the right content to build the memory *machinery* (persistent scope, retrieval, supersession, the visible memory panel) on before it ever carries riskier cargo. The general memory tiers wait until play shows what actually recurs and there is a baseline to evaluate them against.

## 7. Acceptance

- **New Game** clears working memory (fresh target, hypotheses, evidence) and **preserves definitions**, which are available to agents from question one.
- **Full Reset** clears both; definitions gone.
- Definitions remain claims with provenance; the log/fold stays pure; replay still works across the New Game boundary.
- The human can **see** current persistent definitions, and can **challenge/supersede** a retrieved one via the existing clarification path.
- No general cross-game memory added — definitions only.

## 8. First move

Add the scope distinction to the event/data model first (§1, §3) — that is the load-bearing change; the two buttons are thin wrappers over it. Then wire **New Game** to reset working scope only, add **Full Reset** for both, surface a small **definitions panel** so persistent memory is visible, and confirm the challenge-a-retrieved-definition path works. Then play a second game and check the payoff: it should *not* re-ask what "alive" means — and if you challenge the remembered definition, the new meaning should supersede cleanly.

*End of instructions — 2026-07-07.*
