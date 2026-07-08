# Instructions — grounded refutations (the hallucinated-Skeptic fix)

**For Claude Code, continuing the Twenty Questions observability toy.**
*2026-07-07 · derived and validated against a real game log (the "apple tree" failure) · builds on the fold, the evidence-vs-hypothesis two-layer model, and `h`*

---

## The failure this fixes (read first — it is subtle)

A real game converged correctly on "apple tree" around Q15, then **failed to sign it for the rest of the game because a weak agent hallucinated a false refutation.** The Skeptic asserted *"apple trees produce fruits with a single large pit"* — which is **factually false** (apples have many small seeds, which the Q&A had already established) — and used that false premise to refute "apple tree." That refutation created a **glut** (apple tree asserted by the driller, refuted by the Skeptic), and the gate — working exactly as designed — **abstained** on the contested hypothesis, repeatedly, until the round budget ran out. The correct answer was sitting in the ledger, refuted by a lie.

Notably, the Skeptic **later caught its own error** ("apples DO fit all criteria... I withdraw this refutation") — but the withdrawal did not clear the glut, and the budget was already spent.

**This is the deepest architectural risk (a weak/unreliable agent corrupting the ledger) showing up live, in the adversary role — the worst place for it, because the architecture correctly treats refutations as serious, so a false refutation has real power.** The fix is NOT "make the Skeptic smarter." The fix is to apply the system's own core law — *the model asserts, it never certifies; ungrounded assertions get low grade* — to **refutations**, which are currently accepted at face value.

## The core principle

**A refutation is a claim. It must be graded like any claim. Its power to block a hypothesis is proportional to its grade — and an ungrounded or evidence-contradicting refutation is low-grade and therefore weak.** Nothing is suppressed (this is option (b), not "reject"): the refutation still enters the ledger and stays visible/auditable, but a weak refutation cannot muster the weight to block a well-grounded hypothesis.

## CRITICAL design boundary — do NOT build a conformity machine

The whole value of the Skeptic is challenging the emerging consensus. If you naively "reject claims that contradict current belief," you silence the adversary and build the *opposite* failure (conformity collapse). The distinction that saves you, and it is the crux:

- A refutation may **freely contradict HYPOTHESES** (the agents' own conjectures — e.g., "it's an apple tree"). **This is legitimate dissent and must ALWAYS be allowed, ungraded-down.** The Skeptic attacking the hypothesis "apple tree" is exactly its job.
- A refutation may **NOT rest on a factual basis that is ungrounded or that contradicts EVIDENCE** (the oracle's grounded answers). *That* is where the error lives, and that is what gets graded down.

So consistency-checking applies against the **evidence layer**, never against the **hypothesis layer**. You are not saying "don't disagree." You are saying "your disagreement cannot be built on a made-up fact or a fact that contradicts what the oracle already told us." This maps onto the two-layer claim model already in the build: **evidence** (oracle answers — grounded, high `h`) vs. **hypotheses** (agent conjectures — graded, contestable).

## Two mechanisms (BOTH are needed — the log contains both diseases)

Validation against the log showed a single "check refutation against grounded facts" rule is **insufficient**, because the Skeptic's false premise was about *apples in general* ("apples have pits"), not about the *target* — and apple-facts are not in the ledger. So:

### Mechanism 1 (PRIMARY — this is what catches the hallucination): a refutation's factual basis must be grounded, not merely asserted.
The Skeptic's premise "apple trees have a single large pit" is a **bare factual assertion with no grounding** — the agent hallucinating a fact about the world. Under the core law, an ungrounded generator-assertion gets **low `h`**. Therefore a refutation whose load-bearing premise is an ungrounded factual assertion is a **low-grade (weak) refutation**.
- This catches the actual error because the false premise's defining property is that it is **ungrounded** — detectable without any external botanical knowledge.
- Implementation: when an agent refutes, its stated basis should be classifiable as either (a) grounded in oracle answers / prior established facts, or (b) a bare factual assertion the agent is introducing. Case (b) → low grade for the refutation. (The agent's refutation text already states its basis; the grader evaluates whether that basis traces to evidence or is newly asserted.)

### Mechanism 2 (SECONDARY — catches a *different* bug in the same log): consistency against oracle-grounded facts.
If a refutation's basis (or any claim) **directly contradicts an oracle-grounded fact**, it is inconsistent-with-evidence → low grade / flagged conflict.
- In the log this fires on a **separate** problem: at Q "is the fruit red when ripe?" the Oracle answered **NO** (event 58), then the same question was re-asked and answered **YES** (event 64). That is a **contradiction in the evidence layer itself** — two grounded facts that conflict (an ambiguous-question / oracle-inconsistency, i.e. the "apple problem" again). This must be surfaced as a real evidence-layer glut, and is exactly what the clarification feature is meant to prevent.

**Both mechanisms ship.** Mechanism 1 makes weak/hallucinated refutations weak. Mechanism 2 catches inconsistent grounded facts (and ties into clarification).

## Option (b) handling — grade, do not suppress

When a refutation is caught (ungrounded basis, or contradicts evidence):
- It **still enters the ledger** as a claim, with provenance, **visible** — nothing is hidden or deleted (consistent with "the model asserts, `h` grades, nothing is suppressed").
- Its **grade is low**, reflecting the ungrounded/inconsistent basis.
- A hypothesis's belief state is the **net of its corroborations and refutations weighted by their grades.** A well-grounded assertion (decent grade) vs. a low-grade refutation → **net positive support; the glut is asymmetric.**
- The **gate** signs when net support clears θ with cardinality 1. An asymmetric glut (strong assertion, weak refutation) should **not** read as a symmetric blocking glut. (In the log: "apple tree" was well-grounded; the false refutation was ungrounded; the gate should have signed, or been far closer to signing, instead of flatly abstaining.)

## Companion fix — withdrawal clears the glut

When an agent **withdraws** a refutation (the Skeptic's "I withdraw this refutation" at event 67), that withdrawal must **supersede** the refutation (strike/supersede machinery from the algebra, applied to refutations), **releasing the hypothesis it was blocking** and giving the gate another chance to evaluate. A retracted refutation should not keep exerting force. (Retain the withdrawn refutation as trace, per the never-delete discipline — it is auditable history, just no longer live.)

## Validated behavior (trace the fix against the log to confirm)

After the fix, re-running the log's logic should yield:
- Event 60: driller asserts "apple tree," **well-grounded** in oracle answers → decent grade.
- Event 61: Skeptic refutes on the ungrounded premise "apples have a pit" → **Mechanism 1 → low-grade refutation.**
- Belief state: "apple tree" = decent-grade assertion **net over** low-grade refutation → **net positive, asymmetric glut.**
- Gate: sees net-positive, cardinality→1 → **signs "apple tree"** (or is much closer to signing than the real log's flat abstention). **Fix working.**
- Separately, events 58 vs 64: contradictory oracle answers on "red when ripe" → **Mechanism 2 → evidence-layer glut surfaced** (and a candidate for clarification).

**Honest boundary (do not over-claim):** this resolves **asymmetric** gluts — a well-grounded hypothesis attacked by a poorly-grounded refutation. It does **not** resolve genuinely **symmetric** contests (weak-grounded hypothesis vs. weak-grounded refutation) — and there the gate should still (correctly) abstain. The fix's power comes from grounding *asymmetry*; where grounding is symmetric-and-weak, honest abstention remains the right behavior.

## Everything stays on the fold

All of this is claims and events: refutations are claims with grades; grading-down is a property computed in the fold from the claim's basis and the evidence; withdrawal is a supersede event. **No mutable side state.** Replay must show: the refutation entering, its low grade, the asymmetric belief state, and (if it happens) the withdrawal clearing the glut — the whole episode legible in the timeline. This episode is a *showcase* for the instrument: you should be able to scrub back and watch a hallucinated refutation fail to block a well-grounded truth.

## What NOT to build

- Do **not** add external fact-checking / world-knowledge lookup (no tools yet). Mechanism 1 deliberately needs none — it works by detecting *ungrounded* basis, not by verifying the fact. Keep it internal.
- Do **not** let consistency-checking touch the hypothesis layer (the conformity-machine trap). Evidence layer only.
- Do **not** suppress/reject refutations (that is option (a)); grade them (option (b)).

## Acceptance

- A refutation whose factual basis is an **ungrounded assertion** receives a **low grade** and **cannot block a well-grounded hypothesis** (the gate signs the asymmetric case).
- A refutation is **still free to contradict hypotheses** without penalty (dissent preserved).
- A claim/refutation that **contradicts an oracle-grounded fact** is flagged/low-graded (evidence-layer consistency), and contradictory oracle answers surface as an evidence glut.
- A **withdrawn refutation supersedes itself and releases** the blocked hypothesis.
- Nothing is deleted or hidden; refutations remain visible with their grades; **replay shows** the whole episode.
- Re-playing the apple-tree scenario now **signs "apple tree"** instead of abstaining to budget exhaustion.

## First move

Add refutation grading first: classify a refutation's stated basis as grounded-in-evidence vs. bare-assertion, and grade accordingly (Mechanism 1). Then make belief state the grade-weighted net of assertions and refutations, and let the gate read asymmetric gluts as signable when net support clears θ. Then wire withdrawal→supersede→release. Then add evidence-layer consistency (Mechanism 2) and surface contradictory oracle answers. Then re-play the apple-tree game and confirm it signs.

*End of instructions — 2026-07-07.*
