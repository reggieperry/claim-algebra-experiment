# Validated against the algebra — the unpinned-referent fix

**For the committee. To be run by Claude Code's adversarial process.**
*2026-07-07 · a proposal checked against the actual `claim-algebra.html` and `claim-calculus.html` formal definitions, which corrected the initial idea · read alongside the apple/tomato non-convergence log*

> **Errata — read `unpinned-referent-decision-record.md` first.** The committee evaluated this proposal and
> returned SOUND-WITH-CORRECTION. The structural claim holds (no type change; the algebra already carries
> `(deal, concept)` slots). But the safety claim below — *"unresolved OR gluted → blocks by Theorem 6.7"* —
> is **wrong for the glut half**, confirmed against Def 6.5, Theorem 6.7's proof, and Remark 6.8, and
> against the shipped credit code. `⊗ₖ` is the knowledge-meet, so `glut ⊗ₖ x = x` **launders** a gluted
> concept-slot (con `= c·0 = 0` → clean `True` → the gate signs), reintroducing CWS. The fix must conjoin
> the contested case by `∧ₜ` (`truthMeet`) or gate each concept-slot independently — never bare `⊗ₖ` — and
> the `⊗ₖ`+Theorem-6.7 justification for the glut case must be struck. The `CreditNetwork` pipeline already
> runs this correction (`∧ₜ` for a contested conjunct, the conflict branched locally). Recommendation:
> wire the convergence monitor's hand-off first; multi-slot decomposition is premature and safe only in the
> corrected form. The decision record governs on any disagreement with this write-up.

---

## The failure (in the algebra's own terms)

Twenty Questions, human thinking "apple." The game reached the right neighborhood (flowering plant, cultivated for food, seeds-in-fruit, red when ripe) and then collapsed at e-58 when the Skeptic correctly observed that the question "is it harvested and eaten at maturity" is about **the fruit**, whereas earlier questions ("is it a tree," "cultivated for food") are about **the plant/organism**. The society had been accumulating evidence about **two different things** under one belief state, and the incompatibility surfaced as a glut that never resolved (e-60/66/72 → inconclusive at e-73). The convergence monitor correctly flagged it (e-59) but nothing acted on the flag.

## What the algebra actually says (the definitions that decide this)

Reading the formal object, not a reconstruction:

- **Def 2.4 (testimony).** A testimony is `t : A ⇀ ℕ[X]×ℕ[X]` — a partial map from **candidate values** `A` to graded pairs. `cand(t)` is the set of candidate values; the channels are per-candidate.
- **Def (the slot), §3.** *"A slot is one **(deal, concept)** position whose evidence accumulates over time; the calculus describes one slot."* — **The slot is already keyed by a subject.** In the credit domain the key is `(deal, concept)`: a specific deal, a specific concept. The candidate map `A` holds the possible **values of that concept for that subject**.
- **Def 6.5 (derivation, ⊗ₖ)** and **Def 6.6 (derivation term).** Values at one slot can be **derived** from values at other slots by the knowledge-meet conjunction `⊗ₖ`; a derivation is a tree `leaf(E) | node_f(D₁, D₂)`.
- **Theorem 6.7 (fail-closed propagation).** *"If any leaf of a derivation D is a gap, then corner(⟦D⟧) = N at the root, and the gate blocks."* — Proven. A single unresolved concept-slot blocks the whole conjoined answer.

## The corrected diagnosis

The initial proposal (add a `subject` field to `Claim`, scope `Testimony` by subject) was **half-wrong against the actual object**, and the docs corrected it:

**The algebra ALREADY has the subject — it is the slot key `(deal, concept)`.** The referent is not implicit and missing; it is explicit at the slot level. What went wrong in Twenty Questions is not a missing type field — it is that **the game ran ONE slot ("the target") where the problem needs SEVERAL, keyed by concept.** "Is it a tree" is evidence about the concept `organism-identity`; "is it harvested and eaten" is evidence about the concept `fruit-identity`. These are **different concepts about the same subject** → in the algebra's own scheme, **different slots** — `(target, organism-identity)` and `(target, fruit-identity)`. The bug is that the toy **collapsed multiple concepts into one slot**, so evidence about different concepts landed in one candidate map and one channel-total, and the e-58 refutation (really: "this evidence is about a *different concept*") registered as a con on the *shared* slot → the glut.

## The fix, and the answer to "does Claim or Testimony change?"

**Neither type changes.** This is the validated result, and it is *stronger* than the initial proposal:

- The algebra **already** provides the subject (slot key) and **already** provides cross-concept conjunction (`⊗ₖ`, Def 6.5) with **proven** fail-closed propagation **on gaps** (Theorem 6.7; a *glut* conjunct is NOT blocked by `⊗ₖ` — certify by `∧ₜ`, see errata).
- So the fix is **not** a new field on `Claim[A]`, **not** a modification to `Testimony`. The type system is already sufficient. The apple/tomato bug is the Twenty Questions **implementation under-using the existing structure** — one slot doing the work of several — not a gap in the algebra.
- The fix lives entirely in **how the game uses slots**: decompose "the target" into multiple **concept-slots**, and make the final guess a **derivation** (`⊗ₖ`) over them: *"an apple is a thing whose `organism-identity` is apple-tree AND whose `fruit-identity` is apple-fruit."* ~~By Theorem 6.7, if either concept-slot is unresolved or gluted, the conjoined answer is unresolved and the gate blocks — which is exactly the correct behavior, delivered by existing proven machinery.~~ **[RETRACTED — glut half false.** By Theorem 6.7 a *gap* slot blocks the root; a *glut* slot does **not**. `⊗ₖ` is the knowledge-meet and a glut is its identity (`B ⊗ₖ x = x`), so a contested slot conjoined with a clean one launders to a clean `True` and the gate signs (CWS). Certify a contested conjunction with `∧ₜ` (`truthMeet`) or gate each slot independently — never bare `⊗ₖ` at the certification step. See `glut-laundering-correction-instructions.md`.**]**

Under this framing the e-58 refutation stops being a frame-shattering bomb: "harvested and eaten" is simply evidence on the `fruit-identity` slot, not a con on the `organism-identity` slot. The two slots carry their own channels; no false glut forms; the derivation conjoins them fail-closed.

## The genuinely new requirement (where the real work is)

The one thing the algebra does **not** already handle, and the one thing Twenty Questions needs that the credit domain did not:

**In the credit domain, slots are STATIC — fixed by a schema known in advance (a deal has known concepts). In Twenty Questions, the concepts are NOT known ahead of time.** The game cannot know that "the target" must split into `organism-identity` and `fruit-identity` until the ambiguity manifests. So the decomposition is **dynamic and discovered**, not static and scheduled. The new mechanism required is:

> **Introduce a new concept-slot mid-game when a referent turns out to be compound.**

And introducing it is a **semantic act** (recognizing that "target" is plant-vs-fruit ambiguous). Therefore — consistent with every prior fix — it lives in an **agent**, not in the algebra and not in the (non-generative) librarian. The split holds and sharpens:

- **Agent (generative, semantic):** proposes a new concept-slot / a referent-decomposition ("the target may be compound: an `organism-identity` and a `fruit-identity`"). This is the "generation" half.
- **Algebra (mechanical, proven):** the resulting multi-slot **derivation** `⊗ₖ` discriminates and conjoins fail-closed **on gaps** (Def 6.5, Theorem 6.7; certify a *contested* conjunct by `∧ₜ`, not `⊗ₖ` — see errata). This is the "discrimination" half — existing machinery, no new operation.
- **Trigger:** the **convergence monitor's non-convergence flag** is the natural signal that a referent may be unpinned (a compound referent conflated into one slot produces exactly the thrashing/persistent-glut the monitor detects). The monitor fires → an agent proposes the decomposition → the algebra conjoins the resulting slots.

So the three mechanisms compose: **monitor detects (structural) → agent decomposes the referent (semantic) → algebra conjoins the concept-slots fail-closed (mechanical).**

## Why this is safe (preliminary — for the committee to break)

- **No type change** means no risk to the proven properties of `Testimony`/`Claim`; the carrier and its laws are untouched.
- **Uses `⊗ₖ` and Theorem 6.7 as-is** — the fail-closed conjunction is already proven **for gaps only**; a multi-concept answer that conjoins concept-slots inherits that proof for the gap case. ~~A gap or glut on any concept-slot blocks the root (Theorem 6.7), which is the desired behavior.~~ **[RETRACTED — a *gap* blocks the root; a *glut* does not (`⊗ₖ` launders it, `B ⊗ₖ x = x`). Certify with `∧ₜ` or per-slot gating. See `glut-laundering-correction-instructions.md`.]**
- **The single-writer discipline (Remark 5.4)** already applies per slot; adding slots does not violate it — each new concept-slot is its own serialized position.
- **The decomposition act is quarantined in an agent** (semantic, generative), keeping the librarian non-generative and the algebra mechanical — the same decide/execute (here decompose/conjoin) split that survived the §C and retirement audits.

## What the committee must adversarially check (the real risks)

1. **Does dynamic slot introduction interact safely with the already-verified fixes?** Retirement, channel-asymmetry, the convergence monitor, and the gate now operate over a *set of concept-slots plus a derivation*, not a single slot. Does per-concept-slot evaluation preserve their proven safety, or does conjoining slots open a new fail-open? (This is the same class of check §C required — a structural change rippling into verified machinery.)
2. **Over-decomposition / explosion.** Every referent is ambiguous if you look hard enough ("apple" = fruit, tree, company, color...). If agents propose concept-slots freely, the derivation explodes combinatorially. The trigger (monitor-flag-gated) is the proposed control — is it sufficient, or does decomposition need a stronger brake (e.g. only decompose the slot the thrash localizes to)?
3. **Who conjoins, and is the conjunction structure itself a semantic judgment?** Deciding that "apple = organism-identity ⊗ fruit-identity" (rather than some other decomposition) is a semantic act. Is the *structure* of the derivation (which slots, conjoined how) something an agent proposes and the algebra executes — or does the librarian/gate end up making a semantic call about how to conjoin? (Guard the non-generativity line.)
4. **Metamorphic check.** Does the multi-slot machinery remain non-generative where it must? Permuting claim *content* while holding slot/channel *structure* fixed must not change mechanical outcomes (gate, retirement, conjunction); only the agent's *decomposition proposal* may depend on content.
5. **The convergence-flag hand-off is not yet wired** (the log shows the flag firing at e-59 and being ignored). The decomposition trigger depends on that hand-off existing. Sequence: wire the flag to *act* (surface / trigger) before relying on it to trigger decomposition.

## Summary for the committee

- **Corrected result:** neither `Testimony` nor `Claim` changes. The algebra already handles referent-decomposition via `(deal, concept)` **slots** + `⊗ₖ` **derivation** (Def 6.5), fail-closed **on gaps** (Theorem 6.7). The apple/tomato bug is the toy **collapsing multiple concepts into one slot**, not an algebra gap.
- **The fix:** decompose "the target" into concept-slots; make the guess a derivation over them for value, but ~~fail-closed propagation (Theorem 6.7) handles unresolved/gluted concept-slots correctly, as proven.~~ **[RETRACTED — Theorem 6.7 handles *gap* slots (proven), not *glut* slots, which `⊗ₖ` launders to a clean sign. Certify a contested conjunction with `∧ₜ` or per-slot gating. See `glut-laundering-correction-instructions.md`; the shipped `Testimony.conjoin` + `ConjoinSuite` carry the fix and its proof.]**
- **The new requirement:** **dynamic** slot introduction (concepts unknown in advance, unlike the credit schema) — a **semantic agent act**, triggered by the **convergence monitor**, with the algebra conjoining mechanically. Monitor detects → agent decomposes → algebra conjoins.
- **The decision was reached by reading the formal definitions, which corrected the initial (type-extension) proposal** — but this write-up is still correlated Claude reasoning. The independent confirmation is the adversarial pressure-test (risks 1–5) and, ultimately, the running system: a decomposed apple game that conjoins organism- and fruit-identity slots and either signs "apple" or blocks fail-closed — never gluts on a conflated slot.

*End of write-up — 2026-07-07.*
