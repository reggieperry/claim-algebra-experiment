# Addendum — adversarial review of the hypothesis-lifecycle fix

**For Claude Code, before building the channel-asymmetry retirement fix.**
*2026-07-07 · a fair-control pass on the design-committee's conclusion · read alongside `hypothesis-lifecycle-instructions.md`, which it does not replace*

---

## Why this addendum exists

The hypothesis-lifecycle instructions were reviewed by a design committee (Claude agents) which concluded they were sound. That review is a **useful consistency check but NOT independent validation** — the reviewers and the author are the same model reasoning from the same instructions, so their agreement is *correlated*, not independent. This is, precisely, the situation the architecture treats with suspicion (agreeing agents sharing a blind spot). Weight the committee's conclusion accordingly: it confirms the design didn't *drift* under re-examination; it does **not** confirm the design is *correct*. Only the running system against real and adversarial logs does that.

This addendum flags three things the correlated review left unexamined — two of which matter *before* you build, because they guard against the fix's **dangerous inverse failure** (over-firing / suppressing legitimate dissent), which no reviewer stress-tested.

---

## Caution 1 — "withdrawn by author" is mechanical; "defeated by later evidence" may hide a judgment

The retirement predicate has two ways a pro-channel entry becomes "not live support":

- **(a) Withdrawn by author** — the asserting agent itself later refuted the claim (e.g. driller at e-20 refuting its own e-14 assertion). This is **genuinely mechanical**: it is a pure fact about the log (did the same agent that asserted X later refute X?). No judgment. Safe for the librarian.
- **(b) Defeated by subsequent evidence** — the assertion predates an oracle answer that *contradicts* it (e.g. e-14 "plant or fungus" predates and is killed by e-17 "plant: YES"). **This is NOT automatically mechanical.** Deciding "does this later oracle answer *contradict* this earlier assertion?" is **itself an inference** — and if the librarian makes it, a substantive judgment has been smuggled into the supposedly non-generative librarian, violating the design's own core discipline (librarians read the channel balance; they do not judge).

**The clean log case obscured this** because "plant: YES kills plant-or-fungus" is trivially obvious. The general case is not. "Defeated by later evidence" requires *someone* to determine contradiction, and that someone must not be a non-generative librarian applying a rule it can't actually apply mechanically.

**Build implication — do this:**
- Implement the **(a) author-withdrawal** trigger FIRST and rely on it primarily. It is unambiguously a rule over the log.
- For **(b) evidence-defeat**, do NOT have the librarian infer contradiction. Instead, treat "this assertion is defeated by evidence E" as **a refutation that an agent must actually post** — i.e. an agent (the Skeptic or any solving agent) explicitly refutes the stale assertion citing the evidence, which puts a real entry in the con channel and (combined with no live defense) triggers retirement mechanically. In the log this is *exactly what happened* — e-19 and e-20 are agents explicitly refuting, not the librarian silently inferring defeat. So the mechanical path is: **agents post the refutations (substantive judgment, where it belongs); the librarian only reads "no live pro support + standing con" (mechanical).** The librarian never itself decides an assertion is "defeated by evidence" — an agent's posted refutation is what establishes that, and the librarian just counts channels.
- **Net:** keep the librarian counting channel entries, never judging contradiction. If you find yourself writing librarian code that decides whether evidence contradicts an assertion, stop — that logic belongs in an agent's refutation, not the librarian's predicate.

## Caution 2 — the review validated the FAVORABLE case and never tested the DANGEROUS inverse (this is the important one)

The log exercises the case we already understood: a *defeated* hypothesis wrongly jamming the gate, fixed by retiring it. Both the instructions and the committee honestly noted the **real-glut HOLD case was not exercised** — but noting a gap is not testing it. The genuinely dangerous failure is the **inverse of the plant-or-fungus bug**:

> **The system wrongly retiring a hypothesis that is actually CONTESTED (both channels live) — thereby suppressing legitimate dissent (the conformity-collapse risk).**

The plant-or-fungus fix makes the gate *less* conservative (it stops holding on defeated claims). The risk is that it becomes *too* aggressive and starts retiring things that should be held. **No reviewer built the test that catches this**, because it is the test against the shared blind spot — the fix's own over-firing.

**Build implication — construct and pass this adversarial test BEFORE trusting the fix:**
- Build a **synthetic log where a hypothesis has genuine LIVE support AND live refutation concurrently** — e.g. agent A asserts H and does NOT withdraw; agent B refutes H on grounds that are NOT backed by a contradicting oracle answer; the evidence does not settle it. Both channels live, no withdrawal, no evidence-defeat.
- **Confirm the system HOLDS (abstains) on H — does NOT retire it.** This is a *real glut* and must be preserved as one.
- Add a variant: A asserts H, B refutes H, then later A *re-asserts / defends* H (fresh live support after the refutation). Confirm H stays live (contested), not retired — live support on both sides must block retirement even if a refutation exists.
- This test is **more important than re-confirming the plant-or-fungus case you already know works**, because it guards the direction the fix could break. A fix that signs apple-tree correctly but also silences a legitimate dissenting hypothesis has traded one failure for a worse one.

## Caution 3 — do not treat "the committee agreed" as validation (weight it correctly)

Confidence attaches at the *check*, not at the *concurrence*. Two Claude instances agreeing is correlated evidence — the same discipline the system applies to agents applies to its reviewers. The events that actually validate this fix are:

1. The running system, replaying the plant-or-fungus log, **retires at e-20 and signs "apple tree" at e-66** (the favorable case — confirms the fix does what it should).
2. The running system, on the **adversarial contested-hypothesis log** (Caution 2), **HOLDS and does not retire** (confirms the fix does not over-fire).

Until both pass against running code, the design is *plausible*, not *validated* — regardless of how many Claude reviewers concur. Do not let committee agreement raise the confidence bar that only the running checks are entitled to raise.

---

## Consolidated build sequence (folds the cautions into the original first-move)

1. **Author-withdrawal retirement** (Caution 1a): implement the mechanical trigger — a pro-channel entry is dead if the same agent that asserted it later refuted it. Retire when no live pro support remains + standing con. Pure rule over the log.
2. **Evidence-defeat via posted refutations, NOT librarian inference** (Caution 1b): the librarian never decides an assertion is contradicted by evidence; an agent's explicitly-posted refutation establishes that, and the librarian only counts channels. Verify the librarian code contains no contradiction-judging logic.
3. **Retire to trace, off the live board, recoverable** (as original §B).
4. **Favorable-case test:** replay plant-or-fungus → retires at e-20, wasted refutations stop, **signs "apple tree" at e-66**.
5. **Adversarial over-firing test** (Caution 2 — the one the review missed): synthetic contested-hypothesis log (both channels live, no withdrawal, no evidence-defeat) → system **HOLDS, does not retire**. Plus the re-assertion variant. **This is the gate on trusting the fix.**
6. **Gate scoping** (original §C): leading-candidate evaluation, as the backstop for the unretired case. (Still not exercised by the plant-or-fungus log; the adversarial log in step 5 will exercise the HOLD path that scoping must respect.)
7. **Recovery path** (original §B): confirm retire-to-trace is reversible if later evidence restores live support. (Unvalidated by either log; build it, and ideally add a synthetic test where new evidence resurrects a retired hypothesis.)

## The one-line summary for the committee

The design is sound *as far as the plant-or-fungus log tests it*, and the committee correctly confirmed it didn't drift. But the log only tests the favorable direction. **Before trusting the fix, build the adversarial test that proves it does not over-fire and retire genuinely-contested hypotheses** — the failure the correlated review could not see because it is the review's own blind spot. And keep the librarian counting channels, never judging contradiction: agents post refutations (judgment), the librarian reads the balance (mechanical).

*End of addendum — 2026-07-07.*
