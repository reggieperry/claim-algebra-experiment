# Correction — the glut-laundering error in `unpinned-referent-algebra-validation.md`

**For Claude Code and the review committee. This document corrects a false safety claim in the live project doc, states the correct interpretation, and specifies the running-system check that verifies the correction. It supersedes the affected claims wherever they appear.** 2026-07-08. Grounded in the committee's source-check (recorded in `unpinned-referent-decision-record.md`), the formal semantics in `claim-algebra.html` §6, and the guard pattern already shipped in `CreditNetwork.scala`.

---

## What the document claims, and which half is wrong

`unpinned-referent-algebra-validation.md` asserts, at three places (lines 33, 56, and 70–71), that conjoining concept-slots by the derivation operation `⊗ₖ` is fail-closed for contested slots:

> "if either concept-slot is unresolved **or gluted**, the conjoined answer is unresolved and the gate blocks (Theorem 6.7)" · "A gap **or glut** on any concept-slot blocks the root (Theorem 6.7)"

The gap half is proven. **The glut half is false.** Theorem 6.7 is gap-only: its proof runs through `corner(t) = N ⟺ t = ε` and the fact that the empty testimony `ε` annihilates `⊗ₖ`. Nothing in the proof touches `B`. A gluted slot is non-empty, so the theorem never reaches it, and the operation's actual behavior on `B` is the opposite of blocking.

I wrote the original document; the committee refuted the glut claim by checking it against the formal definitions and the shipped credit pipeline. The refutation was recorded in the decision record but never attached to the document itself, which has since been carried in the project knowledge base with the false claim intact. This document is the correction the record has been missing. Treat the three quoted claims as retracted; everything else in the original (the slot-key analysis, the one-slot-collapse diagnosis, the dynamic slot-introduction sketch) stands.

## Why the glut claim is false

The mechanism is short and worth holding exactly, because it recurs.

**At the corner level.** In the knowledge order, `N ≤ T, F ≤ B`: a gap is knowledge-bottom, a glut is knowledge-top. `⊗ₖ` is the knowledge-meet. The bottom of a lattice is its meet's annihilator and the top is its identity, so:

| conjoined with a clean `T` slot | root corner | gate (needs `T`) |
|---|---|---|
| `N ⊗ₖ T` (gap leaf) | `N` | blocks — Theorem 6.7, proven |
| `B ⊗ₖ T` (glut leaf) | `T` | **signs — the contest has vanished** |
| `B ∧ₜ T` (glut leaf, truth-meet) | `B` | blocks |
| `N ∧ₜ T` (gap leaf, truth-meet) | `N` | blocks |

`B ⊗ₖ x = x` for every `x`. A contested slot conjoined with a clean slot yields the clean slot, unmarked.

**At the channel level** (the provenance view, which is what the fold actually computes). `⊗ₖ` combines both channels by product: root-con = product of leaf-cons. One clean leaf has con = 0, and multiplication by an empty channel zeroes the root's con regardless of how much refutation the contested leaf carries. The refutation evidence is annihilated arithmetically. This is the laundering, stated mechanically: the operation does not overlook the contest, it computes it away.

**At the gate.** The laundered root presents corner `T`, cardinality 1, and a grade rendered from surviving pro-provenance. All four conjuncts of the accept biconditional can pass, and the gate signs a conjunction one of whose conjuncts is actively contested. This is confident-wrong-at-signature, and it is worse than the glut-jam failure the lifecycle work fixed: the jam at least blocked, and the convergence monitor could see the stall. Here the monitor sees a healthy convergence and a clean sign. Nothing flags.

## The correct interpretation

The two operations answer different questions, and the error was feeding the answer to one question into a predicate that asks the other.

`⊗ₖ` is the knowledge-meet: it answers *"what do these sources jointly establish?"* In the knowledge order, contradiction is *more told*, not defective, so the meet treats a glut as surplus information to intersect away. That is the right semantics for merging information content. It is the wrong semantics for certifying, because certification does not ask what was told.

`∧ₜ` is the truth-meet: it answers *"is the conjunction true?"* Its channel form is pro by product, con by sum — every conjunct must support, and any refutation anywhere reaches the root. Under `∧ₜ` the root is `T` iff every leaf is `T`: a gap leaf zeroes root-pro (blocked), a gluted or refuted leaf makes root-con nonzero (blocked). Fully fail-closed for a gate that signs only on `T`.

The gate's predicate is a truth question (`corner = T`). **Using the knowledge-order conjunction to feed a truth-order gate is a category error, and that category error is the entire bug.** Each meet is faithful to its own lattice; the fault was ours in wiring one lattice's conjunction to the other lattice's test.

Two correct constructions, either sufficient:

1. **Conjoin by `∧ₜ`** wherever a multi-slot answer feeds the gate. The glut and the gap both block, and a refuted conjunct carries `F` to the root, which is the desired propagation for a signature.
2. **Gate each concept-slot independently** and sign only if every slot passes all four conjuncts on its own. No cross-slot algebra at sign time at all. Simpler, and at least as strong: it cannot be weaker than any conjunction of the slots, because it *is* the conjunction of the gate's own predicate.

The choice between them is an engineering call (2 is easier to audit; 1 composes if derived intermediate values are needed). What is not a call: `⊗ₖ` may be used to *derive* values, never to *certify* a conjunction. Derivation and certification are different steps, and only `∧ₜ` or per-slot gating may stand at the certification step.

## The precedent in shipped code

None of the above is new design. `CreditNetwork.scala` documents this exact laundering in comments and guards its contested nodes manually: local branching with `∧ₜ` where a node can carry a glut. The correction restates what the shipped pipeline already practices. The residual finding from the decision record also stands: the shipped guard is manual and must-extend — every new contestable node needs the discipline reapplied by hand — and a per-node gate would make it structural rather than remembered. That is a hardening item for the credit pipeline independent of the toy.

## What this changes and what it does not

- **The multi-slot decomposition stays shelved.** The decision (wire the convergence flag to act first; decomposition premature for the toy) is unchanged. This correction exists so that when a multi-slot build does happen, it starts from the true claim rather than the retracted one.
- **The live doc must stop teaching the false claim.** Either amend `unpinned-referent-algebra-validation.md` (retract the three claims, cite this correction) or retire it to trace with this document attached. Leaving it as-is means the project's own memory carries a defeated hypothesis on the live board, which is the stale-hypothesis bug operating on the project itself.
- **The pattern class is now named twice.** The fallible-oracle run found `verify = C ∨ O` signing through its weak branch; this correction finds a conjunction certifying through an identity element that erases contests. Same genus: a certifying path whose guarantee is weaker than its surface reading. The audit discipline is the same in both cases — for each way a signature can be produced, state what it actually guarantees, and check the statement against the semantics rather than the intuition.

## Verification — do not take this document's word

The acceptance test is the still-owed running-system check, now specified. Build it against the real fold and gate, not a model of them.

**Fixture.** Two slots for one subject. `organism-identity` = a glut: at least one candidate with both channels non-empty (assert some candidate, corroborate it, refute it; corner must read `B`). `fruit-identity` = clean: one candidate, corroborated to the floor, corner `T`, cardinality 1, grade ≥ θ.

1. **Reproduce the defect.** Fold the two slots to a root under `⊗ₖ` (derive). Assert: root corner = `T`, root con-channel empty, and the gate's decision on the root is a sign. This is the laundering, observed in the running system.
2. **Confirm the fix, path 1.** Fold the same two slots under `∧ₜ`. Assert: root con-channel non-empty, root corner ≠ `T` (expect `B`), gate blocks.
3. **Confirm the fix, path 2.** Per-slot gating: assert the `organism-identity` slot fails `corner = T` on its own, so the all-slots-pass predicate blocks, regardless of any conjunction.
4. **Confirm 6.7 is undisturbed.** Replace the organism slot with `ε` (a genuine gap). Assert: `⊗ₖ` root corner = `N` and blocks (the theorem's proven half), and `∧ₜ` blocks likewise. The correction is glut-specific; the gap behavior must be identical before and after.
5. **The divergence is the result.** Success = the signatures diverge exactly as stated: derive signs the contested conjunction; truth-meet and per-slot both block; gap blocks under everything. If the running system does not reproduce this divergence, the implementation differs from the formal semantics somewhere, and finding that difference takes priority over every item above — it would mean the fold does not compute what the calculus says.

Log the run and attach it to this document. The correction is accepted when the divergence is observed, not when this argument is read.

## Status ledger

- **Proven and unchanged:** Theorem 6.7, gap case — `ε` annihilates `⊗ₖ`; a gap leaf blocks the root.
- **Retracted:** the glut extension of 6.7 at lines 33, 56, 70–71 of the original doc. `B` is the identity of `⊗ₖ`, not an annihilator; a glut leaf launders.
- **Correct constructions:** certify by `∧ₜ` or by per-slot gating; use `⊗ₖ` for derivation only, never at the certification step.
- **Verified when:** the five-step divergence check passes against the running fold and gate.
- **Open, unchanged by this correction:** wire-the-flag-first sequencing; the credit pipeline's manual-guard hardening; the shelved multi-slot decomposition.

*End — 2026-07-08.*
