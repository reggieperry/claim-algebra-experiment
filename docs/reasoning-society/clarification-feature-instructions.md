# Instructions — build the clarification feature

**For Claude Code, continuing the Twenty Questions observability toy.**
*2026-07-07 · builds on the existing fold/event-log architecture · PREREQUISITE for the two-tier reset / definitions-as-memory work (build this first)*

---

## Sequencing note (why this comes first)

A prior set of instructions covered a two-tier reset (New Game vs Full Reset) that **preserves definitions**. But definitions don't exist yet — nothing in the system produces them. This feature is what produces them. So **build clarification first**; the reset/memory work comes after, because it preserves a payload this feature creates. Order: clarification (this doc) → definitions persist as memory → two-tier reset.

## The motivating bug (what this fixes)

In play, the system asked "is it alive?", the human answered "yes" thinking of an apple as living tissue, but the system meant "a living creature currently alive" — for which a picked apple is *no*. The human's answer was **faithfully recorded and wrong at the same time**: cleanly grounded to a question that meant different things to asker and answerer, and it led the society astray with nothing flagging it. This is the core verification problem — confidently reasoning from a mis-grounded input — showing up as question ambiguity. The human caught it manually. The system couldn't.

The fix: let the human **refuse to answer an ambiguous question until its meaning is pinned down**, and record the agreed meaning so it grounds all future uses. This is *definitional grounding* — establishing that a claim means the same thing to both parties **before** it enters the ledger as evidence.

## 1. The core change: a two-move human turn

Right now the human turn is one move: **answer** (yes/no/unknown). Add a second move:

- **Answer** — yes / no / unknown (as now).
- **Challenge** — "what do you mean by '<term>'?" / "define '<term>'" — asked *before* answering.

A challenge **pauses grounding**: the human's answer does not enter the ledger yet. Instead the system responds with the definition it intended (§2). The human may then **answer** against that definition, or **challenge again** if still unclear. Only once the human answers does the evidence enter the ledger — now grounded to the agreed meaning.

So the flow changes from:
`agent asks question → human answers → answer enters ledger`
to:
`agent asks question → [human may challenge → definition given → repeat until clear] → human answers → answer enters ledger (grounded to the agreed meaning)`

That inserted middle step is the whole feature.

## 2. Who answers the clarification — the asking agent

When the human challenges "is it alive?", **the agent that proposed the question** answers what it meant (not a system-level oracle). Rationale: the proposing agent should have to state what it meant, and **if it can't crisply define its own question, that is itself a signal the question was ill-formed** — which the human (and you, watching) will see. Making the agent defend its question's meaning is more informative than handing down definitions from the system.

(If the current architecture makes "the asking agent responds" awkward, a system/moderator response is an acceptable fallback for now — but prefer the asking agent, because its self-definition is diagnostic.)

## 3. Definitions are CLAIMS in the ledger (the key discipline)

The definition the agent gives is **not ephemeral chat and not a side key-value store.** It enters the ledger as a **claim**, with provenance:

- A definition claim records: the **term**, its **agreed meaning**, and **provenance** — which clarification exchange established it, which agent gave it, in which game.
- Once established, the definition is **available to all agents** for the rest of the game, so every future use of that term is grounded to the agreed meaning — the society now reasons with shared vocabulary, consistently.
- Because it is a claim with provenance, it is later **challengeable and supersedable** (this is what the future memory/reset work builds on — a remembered definition can be challenged in a new game, and the new meaning supersedes the old, trace retained).

This is the difference between patching one confused question and building **shared grounded vocabulary** — and it is what makes definitions the natural first content of semantic memory later. Keep them as claims.

## 4. Everything is events (stay on the fold)

Consistent with the event-log/pure-fold foundation: every step of the clarification exchange is a **logged event**. Add event types:

- `clarification_requested` (human challenged a question; includes the term/question)
- `definition_given` (agent/system supplied the meaning; includes term + meaning + which agent)
- (existing) `answer_given` now carries a reference to the governing definition(s) when one was established, so the grounded answer records *what it was grounded to*.

Do not introduce mutable side state for the exchange — it all goes in the log, so it all folds, so **replay shows the disambiguation happening.** Scrubbing back should let the human watch the moment the meaning got negotiated — which is exactly the reasoning-made-visible the instrument exists for. The clarification is part of the visible reasoning trace, not hidden.

## 5. What the human sees

- On the current question, a **Challenge / "define term"** control alongside the yes/no/unknown answer control.
- When a definition is given, show it clearly (it is a claim — show it like the others, with its provenance/source agent).
- A small, running list of **definitions established this game** (this becomes the seed of the visible memory panel in the later memory work — building it now is fine and useful).
- In replay, the `clarification_requested` → `definition_given` → `answer_given` sequence should be visible in the event stream and reconstructable at the playhead.

## 6. Scope discipline (what NOT to build in this pass)

- Do **not** build cross-game persistence yet — definitions live in the current game for now. Making them *persist* across games (and the two-tier reset that preserves them) is the **next** pass, and it builds on this one. Keep definitions as claims so that next step is clean, but don't wire persistence here.
- Do **not** add proactive disambiguation yet (agents pre-empting "is it alive? — I mean a living creature"). That is a later, richer behavior the system could *learn* from accumulated challenges. For now: definitions are given **only on challenge**, which keeps the human in the definitional-adversary seat and keeps the build small.

## 7. Acceptance

- The human can **challenge** a question ("define '<term>'") *before* answering; the answer does not enter the ledger until after clarification.
- The **asking agent** (preferred) supplies the meaning; the human can challenge again or then answer.
- The definition enters the ledger as a **claim with provenance**, available to all agents for the rest of the game; subsequent uses of the term are grounded to it.
- Every step is a **logged event**; **replay reconstructs the disambiguation** at the playhead.
- Definitions established this game are **visible** to the human.
- No cross-game persistence and no proactive disambiguation in this pass.

## 8. First move

Add the event types (`clarification_requested`, `definition_given`, and the definition-reference on `answer_given`) and the definition-as-claim structure first — that is the critical change. Then add the **Challenge** control to the human turn, wire the asking-agent response, surface the definitions-this-game list, and confirm replay shows the exchange. Then re-play the apple: ask "is it alive?", challenge it, get the agent's meaning, answer against it — and watch the society reason from a correctly-grounded answer, with the whole negotiation visible in replay.

*End of instructions — 2026-07-07.*
