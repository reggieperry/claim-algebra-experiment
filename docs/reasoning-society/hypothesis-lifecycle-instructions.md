# Instructions — hypothesis lifecycle: channel-asymmetry retirement & gate scoping

**For Claude Code, continuing the Twenty Questions observability toy.**
*2026-07-07 (rev 2 — channel-asymmetry formulation) · derived and validated against a real game log (the "plant or fungus" jam) · builds on the fold, the pro/con channels, and the supersede/strike machinery · SEQUENCE THIS BEFORE the grounded-refutations fix — this bug bites even when every agent is correct*

---

## The failure this fixes (read first)

A real game **solved itself correctly but never signed**, because a hypothesis the agents had *correctly abandoned* jammed the gate for the entire rest of the game. Trace:

- e-14: driller asserts an early loose hypothesis, **"plant or fungus."** (goes into its **pro channel**)
- e-17: Oracle answers **"plant: YES"** → the "or fungus" disjunct is now dead; that e-14 assertion is *superseded by subsequent evidence*.
- e-19 (Skeptic) and e-20 (driller): **both refute "plant or fungus."** Note e-20: **the driller refutes its own earlier assertion** — the asserter itself withdrew. (these go into its **con channel**)
- e-21 → e-70: **the gate abstains "conflict — a hypothesis is both asserted and refuted" for FIFTY events**, while the Skeptic re-refutes "plant or fungus" **nine times**, and the society meanwhile **correctly identifies apple tree** (asserted e-61, **corroborated e-66**, never refuted). Game ends inconclusive at e-70 with the right answer sitting corroborated and unsigned.

**This is NOT a hallucination (that's the separate apple-tree fix).** Every agent reasoned *correctly*; the refutations were all *true*. The failure is purely **architectural**.

## The core insight (this is the whole fix)

The agents were **in agreement, not in conflict** — everyone agreed "plant or fungus" was obsolete. But the ledger recorded that agreement as a **glut** ("asserted and refuted"), because the system had no way to distinguish two structurally-similar but semantically-opposite states:

- **A REAL glut (genuine live disagreement — the valuable thing the four-valued logic is FOR):** **BOTH channels live** — someone is actively asserting/defending AND someone is actively refuting, concurrently. Real tension. **The gate must HOLD (abstain) — do not sign contested claims.**
- **A DEFEATED claim (this log's false glut):** **pro channel has no LIVE support** (all supporters withdrew, or the only support predates and is defeated by subsequent evidence) **AND con channel has standing refutation.** No tension — the claim is simply beaten. **The gate must NOT be blocked by it — retire it.**

The channels **already encode** the difference. The system just wasn't reading it. "plant or fungus" had: pro channel = one *superseded* assertion (e-14, made before plant:YES, and *withdrawn by its own author* at e-20); con channel = standing, unanimous refutation. **Empty-of-live-support pro + standing con = defeated.** The system read that as a glut and honestly abstained. Fix the reading and the honest gate does the right thing automatically.

**Why this formulation (channel asymmetry) rather than "detect agreement":** you do NOT need to detect that agents agree with each other, or that they refuted "on the same grounds." Those are fuzzy, semi-social judgments. The thing that actually matters is a **pure structural fact about the claim's two channels**: does it have live support? is it under standing refutation? It does not matter *why* everyone abandoned it — only that no one is defending it and it is being refuted. This removes the last bit of interpretation from detection and makes it a clean, non-generative predicate.

## What the fix must do (three linked mechanisms)

### A. Channel-asymmetry detection & retirement — librarian DETECTS + EXECUTES (mechanical, non-generative)
The **librarian** retires a hypothesis when a **pure predicate over its two channels** holds:

> **RETIRE B when: B's pro channel has NO live support, AND B's con channel has standing refutation.**

Where **"no live support"** means: every pro-channel entry for B is either **withdrawn** (its author refuted it — e.g. driller at e-20) or **defeated by subsequent evidence** (asserted *before* an oracle answer that contradicts it — e.g. e-14 predates plant:YES). A pro-channel entry that is *current and evidence-surviving* counts as live support and **blocks** retirement (→ it's a real glut, hold).

- **Detection is a pure rule over the event log / channels** (fold-computable) — no generative judgment, no "do these agree?" check, no grounds-consistency check. Just: *is there live support? is there standing refutation?* This preserves the librarian's anti-confabulation discipline.
- The librarian **does not decide** anything substantive — it reads the channel balance the agents produced and files the consequence. (Consistent with the librarian's role: operates on the agents' conclusions, never makes domain judgments.)

### B. Retirement to trace — remove from the live board, keep recoverable
On retirement, via the supersede/strike machinery:
- B is **removed from the live board** — critically, *removed from what agents are shown as live targets*, so they stop attacking it (this collapses the nine wasted re-refutations to the ones that defeated it).
- "Retired" means **off the live board, not deleted** — retained as **citable trace**.
- **Recoverable.** Trace persists; if later evidence *restores live support* (e.g. new evidence resurrects the claim), it can return. *(Not exercised by this log, but required — do not implement retirement as permanent deletion.)*

### C. Gate scoping — evaluate the leading live candidate, not global conflict
The gate asks whether **the leading live candidate** is signable (Resolved, cardinality 1, grade ≥ θ, and — per the real/defeated distinction above — **not under live two-channel contention**) — **not** whether the whole ledger is conflict-free. A defeated/abandoned hypothesis must **not** block signing a different well-supported live one. This is the **backstop for the case where a defeated claim is not cleanly retired**: even unretired, it cannot hold the gate hostage over the leading candidate.

## Validation against the log (what the trace actually showed)

- e-14 assertion enters "plant or fungus" pro channel.
- e-17 "plant: YES" → the e-14 assertion is now **defeated by subsequent evidence** (predates and is contradicted).
- e-19 refutes; **e-20 the driller refutes its own e-14 assertion** → the sole pro-channel entry is now both *superseded* AND *withdrawn by its author*. **Pro channel has no live support.** Con channel has standing refutation.
- **Channel-asymmetry predicate holds at e-20 → librarian retires "plant or fungus" to trace, off the live board.**
- **The jamming "glut" is gone** — it was never a real glut (no live support was ever on both sides); from e-21 on the gate abstains for the *correct* reason ("no confirmed hypothesis yet") while narrowing continues.
- **The nine wasted re-refutations collapse** — agents no longer see "plant or fungus" as a live target.
- e-61 asserts "apple tree"; **e-66 corroborates**; nothing ever refutes it → live pro support, no con → **the gate SIGNS "apple tree" at e-66**, instead of abstaining to budget exhaustion at e-70. **Fix confirmed.**

**Honestly marked — what the log did NOT exercise:**
- **The gate-scoping backstop (§C) was NOT fired by this log** — retirement (§A+§B) cleared the false glut on its own, so the backstop (for the *unretired* case) was never needed here. Build §C, but know this log validates the retirement path, not the backstop.
- **The recovery/resurrection path was NOT exercised** — no retired hypothesis needed to come back. Build it (retire-to-trace, not delete), but it is unvalidated by this log.
- **The real-glut HOLD behavior was NOT exercised** — this log contained no genuine two-channels-live disagreement. Ensure the predicate correctly *blocks* retirement when live support exists on both sides (a real glut must still HOLD), even though this log has no such case to confirm it.

## Everything stays on the fold

Assertions, refutations, withdrawals, and retirements are all **events**; the pro/con channels and the retirement predicate are **pure functions over the log**; retirement is a **supersede event** moving B to trace. **No mutable side state.** Replay must show: the e-14 assertion, its defeat by e-17, the e-19/e-20 refutations (including the self-withdrawal), the **retirement at e-20** (B visibly leaving the live board), the board proceeding cleanly, and **"apple tree" signing at e-66.** Scrub to e-20 to watch the defeated hypothesis retire; scrub to e-66 to watch the gate finally sign.

## Interaction with the other fix (do not conflate)

This fix (lifecycle/scoping) and the **grounded-refutations** fix are **separate and complementary**:
- **Grounded refutations** stops a *bad* (ungrounded/hallucinated) refutation from blocking a good hypothesis (the *apple-tree* log). Matters *when agents err*.
- **This fix** stops a *defeated* (correctly-abandoned) hypothesis from blocking the leading candidate (the *plant-or-fungus* log). Matters *always* — even when every agent is correct.
- Together the gate becomes robust: it can't be jammed by *bad* refutations, by *defeated* hypotheses, or by *false gluts*. Build this one first (the more fundamental hole); grounded-refutations layers on top.

## The distinction to preserve (real glut vs. defeated claim)

The single most important thing: **do not collapse "contested" and "defeated" into one "glut."**
- **BOTH channels live** (real, current support AND refutation) = **genuine disagreement** = **HOLD / abstain** (the gate should not sign contested claims — this is the four-valued logic working as intended).
- **No live pro support + standing con** = **defeated** = **retire** (not a real conflict; do not let it block the gate).
The overloaded "glut" was conflating these; the fix disambiguates them by reading the channel balance.

## What NOT to build

- Do **not** use "detect agreement" or "consistent grounds" — use the **channel-asymmetry predicate** (no live pro support + standing con). It doesn't matter *why* support vanished, only that it did.
- Do **not** let the librarian *judge* — it reads the channel balance (a pure predicate over the log) and files the consequence. No generative opinion.
- Do **not** retire a claim that still has **live, evidence-surviving support** in its pro channel — that's a real glut; HOLD it.
- Do **not** implement retirement as deletion — retire to *trace*, recoverable.
- Do **not** let this silence legitimate dissent — a *contested* hypothesis (both channels live) is exactly what must be held, not retired. This retires only *defeated* hypotheses (support gone).

## Acceptance

- A hypothesis whose **pro channel has no live support** (all supporters withdrew or are defeated by later evidence) **and whose con channel has standing refutation** is **retired to trace, off the live board**, by a **pure predicate over the channels** — no agreement/grounds-consistency check, no generative judgment.
- A hypothesis with **live support on both sides (real glut)** is **NOT** retired — the gate **HOLDS** (abstains) on it.
- Retired hypotheses are **removed from agents' live targets** (wasted re-refutation loop stops) and are **recoverable** (not deleted).
- The gate evaluates the **leading live candidate**, not global conflict-freeness; a defeated hypothesis cannot block a well-supported live one (backstop for the unretired case).
- Nothing deleted or hidden; retirement visible; **replay shows** retirement at e-20 and signing at e-66.
- Re-playing the plant-or-fungus scenario now **signs "apple tree" at e-66** instead of abstaining to budget exhaustion.

## First move

Implement the channel-asymmetry predicate and retirement first: a pure rule over the log/channels — "no live pro support (withdrawn or evidence-defeated) AND standing con refutation → retire to trace, off the live board." Confirm the plant-or-fungus replay retires at e-20 and the wasted refutations stop. Verify the mirror: a hypothesis with live support on both sides is NOT retired (real glut → hold). Then wire gate scoping (leading-candidate evaluation) as the backstop. Then confirm "apple tree" signs at e-66. Then verify the recovery path exists (retire-to-trace is reversible), even though this log doesn't test it.

*End of instructions (rev 2) — 2026-07-07.*
