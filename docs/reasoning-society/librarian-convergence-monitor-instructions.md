# Instructions — librarian convergence monitor (structural non-convergence detection)

**For Claude Code, continuing the Twenty Questions observability toy.**
*2026-07-07 · derived from the "apple / not-a-living-organism" poisoned-premise log · builds on the fold, the librarian's real-time role, and the belief-state history · a HEALTH MONITOR, not a diagnosis engine*

---

## The failure this addresses (read first — it is a new failure class)

A game where the hidden answer was **"apple"** was **lost at the first question** and the remaining 70 events were the society reasoning impeccably toward a wrong region because it was poisoned at the root.

- e-1: memory recalls the definition of "organism" (metabolizing, reproducing biological entity).
- e-5: "Is the hidden thing a living organism as defined?" — the human, thinking of an apple, answered **NO** (a picked apple is not actively metabolizing/reproducing — *defensible and sincere*).
- That NO is a **grounded, faithfully-recorded, game-fatally-wrong premise.** It ruled out the entire category "fruit / part of a living plant" where the answer actually lives.
- e-6 → e-71: the society *correctly* deduced from "not alive + physical + organic + biologically-formed + once-living-derived" toward **fossils / amber / petrified wood** — a flawless deduction into a region that *cannot contain the answer*. Nothing consolidated; candidates churned (fossilized-material asserted e-33, refuted e-51; petrified wood proposed e-61, refuted e-69); the gate abstained to budget exhaustion at e-71.

**This is the "apple bug" in its fullest form, and its lesson is BLAST RADIUS: a mis-grounded answer at the ROOT invalidates the entire subtree beneath it.** One bad early premise wastes the whole game. (Note the gate's honesty held — faced with an unwinnable game it *abstained* rather than confidently signing "petrified wood." That is correct. But abstaining-after-71-wasted-events is a poor experience, and the system had no way to notice it was stuck.)

## Why the existing clarification feature does NOT catch this

Clarification is **human-initiated and term-scoped**: the human challenges a term they find ambiguous. But here the definition was *present and clear* (e-1), the human had *no reason to challenge* — "apple, not metabolizing, NO" felt obviously correct — and the mismatch was not in the *word* but in the *framing of the whole question relative to the answer*, which is **invisible to the person answering**. **Human-initiated clarification is powerless against confident misunderstanding, because confidence is precisely the absence of the trigger.** The dangerous ambiguity is the kind the human does not notice. So this needs a **system-initiated** mechanism, not the clarification feature.

## The core design decision — a STRUCTURAL health monitor, not a semantic diagnosis

The librarian should catch this in real-time — this fits its existing role (the non-generative maintainer of ledger state at every scope; it already watches the ledger continuously). **BUT there is a trap:** what makes the evidence "suspicious" here ("organic but NOT alive" is a semantically weird combination boxing toward fossils) is a **semantic judgment** — it requires understanding what the words *mean*. The librarian is **non-generative** and must **never** make semantic judgments (same discipline that killed soleView-§C and that Caution 1 forbids). If the librarian tries to detect *that "organic but not alive" is weird*, it is reasoning about meaning → it is no longer a librarian.

**The resolution — detect the STRUCTURE, not the MEANING:**

> The librarian detects that **the search is not converging** — a purely structural fact about the belief-state history, readable with zero understanding of what any claim *means*. It does **not** diagnose *why*. On detecting non-convergence it **raises a flag**; the *diagnosis* ("the 'not alive' answer was mis-framed") is handed to something allowed to judge — the agents (semantic reasoning) or the human (ground truth).

This is the same **detect/diagnose split** that kept §C and retirement honest: the librarian detects a structural condition mechanically; the judgment lives elsewhere. **The librarian flags THAT something is wrong; agents or the human diagnose WHAT.**

## What the monitor must do (structural signals — all pure functions over belief-state history)

The monitor is a **pure function over the sequence of belief-states** (which is itself a fold over the event log — so this stays on the fold, no side state). It watches for *non-convergence*, defined by structural signals, none of which require semantic understanding:

### Signal 1 — no candidate is consolidating (the primary signal)
A **healthy** game narrows toward a small set of live candidates that *accumulate durable support* and converge on one. A **stuck** game churns: candidates are proposed and refuted, nothing accumulates support that *survives*, and the leading candidate keeps *changing* rather than *consolidating*.
- Structural read: over the last K rounds, is any candidate's net durable support *increasing* toward a signable threshold, or is the set of candidates *churning* (turnover) with no candidate consolidating?
- In the log: fossilized-material (e-33) → refuted (e-51); petrified wood (e-61) → refuted (e-69); nothing ever consolidates. **Pure structural fact — no meaning needed:** N rounds in, zero candidates accumulating durable support.

### Signal 2 — persistent unresolved glut
A hypothesis in glut for K+ rounds with no movement toward resolution (neither retiring per the lifecycle fix, nor gaining decisive support). The librarian counts glut *persistence* — it does not need to know what the glut is *about*.
- In the log: fossilized-material sits in glut from e-52 onward, re-refuted repeatedly, never resolving.

### Signal 3 — question budget consumed without convergence
Simple and structural: a large fraction of the round budget spent with the belief-state still showing no signable candidate and no convergence trend. (This is the weakest signal alone but a useful backstop — "we are running out of questions and are nowhere.")

**The flag fires when the signals indicate the search is not converging** (tune the exact combination/threshold empirically — start conservative so it flags late rather than nagging early). The output is a **flag**, not a diagnosis: *"the search is not converging — N rounds, no candidate consolidating, glut persisting."*

## What happens on the flag (hand diagnosis to a judge)

The librarian raises the flag; it does **not** diagnose. Two hand-off paths (build the human one first — it is simplest and this failure fundamentally needs the human's ground truth):

- **To the human (primary):** surface it — *"I'm having trouble converging on an answer. You may want to reconsider an earlier answer — especially an early yes/no that might have been ambiguous."* This lets the human, who holds the ground truth, catch the mis-framed premise (the human is the only one who knows it's an apple). This is the human-in-the-loop role the architecture reserves for exactly this: the human supplies ground truth the system cannot have and can see failures the system is structurally blind to.
- **To the agents (secondary, later):** prompt the agents to do the *semantic* work the librarian cannot — "which established premise, if wrong, would most open up the search?" An agent reasoning about meaning may propose that the "not alive" answer is the suspect (a fruit is a boundary case for "alive"). That is an **agent** making the semantic judgment, correctly, where it belongs — the librarian only counted the structural signal that triggered the ask.

## Why this is the right shape (supporting reasoning, condensed)

- **It fits the librarian's real role** (v1.1: non-generative maintainer of ledger state at every scope) — real-time monitoring of ledger *health* is the same *kind* of act as watching the live/trace boundary.
- **It respects non-generativity** — the trap here is identical to soleView-§C: "is this evidence suspicious?" is semantic; "is the search converging?" is structural. Detecting structure keeps the librarian clean; detecting meaning would break it. The metamorphic check applies: **if permuting the semantic content of the claims (holding the belief-state *structure* — support/refutation/churn — fixed) changes whether the monitor fires, the monitor is making a semantic judgment → reject it.** A correct convergence monitor is invariant to what the claims *mean*; it reads only the shape of support over time.
- **The detect/diagnose split** mirrors the retirement fix (agents decide, librarian executes): here the librarian *detects* non-convergence (structural), agents/human *diagnose* the cause (semantic/ground-truth). Neither does the other's job.
- **It generalizes beyond this bug** — a "search not converging" detector fires on ALL non-convergence: poisoned premise (this log), genuinely-too-hard space, and the ambiguity tax. The librarian need not know *which*; it flags non-convergence and a judge diagnoses. One clean mechanism, multiple failure classes caught.
- **It preserves the gate's honesty** — this does not make the gate sign anything. The gate still abstains on an unwinnable game (correct). The monitor just means the system *notices it is stuck* and *asks for help* instead of silently thrashing to budget exhaustion.

## Everything stays on the fold

The belief-state sequence is a fold over the event log; the convergence monitor is a **pure function over that sequence**; the flag is an **event** (`convergence_warning` or similar) appended to the log. **No mutable side state.** Replay must show the flag firing at the point the search was detected as stuck — so you can scrub to it and see *when* the system noticed. The flag event carries the structural evidence (rounds-without-consolidation, glut-persistence count) — not a semantic diagnosis.

## What NOT to build

- Do **NOT** have the librarian judge that an evidence *combination* is semantically weird/suspicious — that is the forbidden semantic judgment. Detect structural non-convergence only.
- Do **NOT** have the librarian *diagnose* the cause or *name* the suspect premise — it raises a flag; agents or the human diagnose.
- Do **NOT** make the flag change the gate's behavior or manufacture a sign — the gate stays honest; the flag is a request for help, not a permission to guess.
- Do **NOT** make it naggy — tune to flag *late* (clear non-convergence), not on early normal narrowing. Start conservative.
- Do **NOT** add mutable side state — it is a pure function over the folded belief-state history.

## Acceptance

- The monitor is a **pure function over belief-state history** (itself a fold over the log); it emits a **flag event** on structural non-convergence (no candidate consolidating / persistent glut / budget consumed without convergence).
- It passes the **metamorphic check**: permuting claim *content* while holding belief-state *structure* fixed does **not** change whether it fires (proves it is structural, not semantic).
- On the apple/not-alive log, the monitor **fires** (flags non-convergence) at roughly the point the search is visibly thrashing in the fossil region — *without* naming the cause.
- On a **healthy converging** game (e.g. the fixed plant-or-fungus → apple-tree log after the lifecycle fix), the monitor **does NOT fire** (it converges and signs) — no false alarm.
- The flag routes to the **human** ("trouble converging — reconsider an early answer"); the human diagnosis path works. (Agent-diagnosis path optional/later.)
- The gate's behavior is **unchanged** — no sign is manufactured; abstention on genuinely-stuck games is preserved.
- Replay shows the flag event at the point of detection, carrying structural evidence, not a semantic diagnosis.

## First move

Build the belief-state-history convergence monitor as a pure function: track, per round, whether any candidate is consolidating (net durable support trending up) vs. churning, and glut persistence. Emit a `convergence_warning` event when non-convergence is clear. **Run the metamorphic check first** — permute claim contents, hold structure, confirm the fire/no-fire decision is invariant (this is the gate on it being non-generative). Then confirm: fires on the apple/not-alive log (thrashing), does NOT fire on the fixed converging log. Then wire the human-facing flag ("trouble converging — you may want to reconsider an early answer"). Leave the agent-diagnosis path for later.

*End of instructions — 2026-07-07.*
