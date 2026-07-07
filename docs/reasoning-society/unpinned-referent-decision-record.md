# Unpinned-referent (multi-slot derivation) — committee evaluation and decision record

For the record. 2026-07-07. Captures the committee evaluation of `unpinned-referent-algebra-validation.md`
— the proposal to fix the apple/tomato non-convergence by decomposing the single answer-slot into
per-concept slots conjoined by the derivation `⊗ₖ` — against the actual `claim-calculus.html` definitions,
the `claim-algebra` codebase, and the twenty-questions code. Read alongside the proposal and
`librarian-convergence-monitor-instructions.md`.

## The proposal in one paragraph

Twenty Questions, human thinking "apple." The society reached the right neighborhood and then collapsed
when it turned out to be accumulating evidence about **two referents** — the plant/organism ("is it a
tree") and the fruit ("is it harvested and eaten") — under **one** belief-slot. The incompatibility
surfaced as a glut that never resolved; the game went inconclusive, and the convergence monitor flagged
the non-convergence but nothing acted on the flag. The proposal's claim: **neither `Testimony` nor `Claim`
changes.** The algebra already carries the subject as the `(deal, concept)` slot key (Def 2.4, §3), and
already provides cross-concept conjunction `⊗ₖ` (Def 6.5/6.6) with fail-closed propagation (Theorem 6.7);
the toy merely under-used the structure by running one slot where the problem needs several. The fix:
decompose the target into concept-slots and make the guess a derivation over them, with a **dynamic**,
agent-proposed decomposition (concepts are not known ahead of time, unlike the credit schema) triggered by
the convergence monitor.

## Verdict — SOUND-WITH-CORRECTION

The structural half holds. No type change is needed; `Testimony`/`Claim` already carry `(deal, concept)`
slots; the diagnosis (single-slot conflation, not a type gap) is correct — §3 says in as many words that
"the calculus describes one slot," and the toy runs one `Testimony[Answer]` doing several slots' work. The
happy path (`T ⊗ₖ T = T`, signs) and the **gap** path (`N ⊗ₖ x = N`, blocks, Theorem 6.7) are correct as
cited.

The correction is precise and it is the reason this is not a clean pass: **the proposal's safety claim
"unresolved OR gluted → blocks by Theorem 6.7" is false for the glut half.** Theorem 6.7 covers the *gap*
case only. A gluted concept-slot conjoined by `⊗ₖ` does not block — it launders. The fix must conjoin the
con-channels by the truth-meet `∧ₜ` (`Testimony.truthMeet`), or gate each concept-slot independently
before signing the composite, and the `⊗ₖ`+6.7 justification for the glut case must be struck.

## The decisive finding — `⊗ₖ` launders a gluted concept-slot (confirmed three ways)

The hypothesis that decided the evaluation: `⊗ₖ` is the **knowledge-meet**, and the glut (`Both`) is the
knowledge-order top, so `glut ⊗ₖ x = x` — the glut is *absorbed*, not propagated. Confirmed against source:

- **Def 6.5 / `Testimony.derive`.** The derivation con-channel is a **product** of the leaf cons. Worked
  case — organism-slot gluted `(p, c)`, fruit-slot cleanly resolved `(q, 0)` → composite
  `(p·q, c·0) = (p·q, 0)`. The con is zero, so `corner` reads **clean `True`, cardinality 1, and the gate
  signs.** The organism contradiction is laundered away.
- **Theorem 6.7 cannot reach it.** Its proof turns on `corner(t) = N ⟺ t = ε` and the empty-set
  annihilator `ε ⊗ₖ b = ε`. A glut leaf is *non-empty* (both channels populated), so the annihilation step
  never fires. The theorem is gap-laundering only; it says nothing about a glut leaf.
- **Remark 6.8, in the note itself.** `⊗ₖ` uses only the knowledge-meet; an actively refuted input
  (corner `F`) is handled by the truth-meet `∧ₜ`, which carries `F`, not `N`. A glut (`Both`) is worse
  than `F`; the same conclusion applies. `∧ₜ`'s con is `ca·pb + pa·cb + ca·cb`, which is non-zero when a
  conjunct is contested — so it is carried to the root and blocks.

It is slightly worse than "a fail-open." The laundered root reads as a **clean signature**, so the
convergence monitor sees convergence, not thrash — and the proposal's own re-decompose trigger, which
depends on the monitor firing, never activates. As written, the fix would reintroduce exactly the
confidently-wrong-at-signature class the whole apparatus exists to prevent.

## The correction

Two equivalent safe forms; prefer the second.

- **Conjoin the contested case by `∧ₜ` (`truthMeet`)** rather than `⊗ₖ` (`derive`). `∧ₜ` carries a
  refuted/gluted conjunct to the root and blocks.
- **Gate each concept-slot independently** (`corner = True ∧ cardinality = 1`, plus the no-lone-sign
  floor) before signing the composite. This is cleaner: it localizes the block reason to the offending
  concept-slot and does not depend on the exact realization of the fork polynomial. Either way, **delete
  the `⊗ₖ`+Theorem-6.7 safety claim for the glut case from the proposal.**

## The credit pipeline already runs the correction (the empirical check)

The committee's one worry beyond the proposal — that the same laundering is a latent hole in the shipped
credit `⊗ₖ` path — was checked against the code and **refuted**, which is the strongest outcome: the credit
pipeline already knows about the laundering and guards it, using the exact correction recommended here.

- `CreditNetwork.scala` states the laundering outright: "`⊗ₖ` downstream would wash it out: a genuine debt
  glut," and "the debt conflict is branched on HERE, not at the final gate, because `⊗ₖ` downstream would
  wash it out." So `⊗ₖ` absorbing a glut is a documented, code-level fact, not a theoretical worry.
- The pipeline uses **`∧ₜ` (`truthMeet`) for a contested conjunct** and `⊗ₖ` only for a merely-*missing*
  one, and it **branches the conflict locally, before the final gate**, so `⊗ₖ` cannot launder it
  downstream. That is precisely the committee's recommended correction, already in production. The credit
  path is the worked precedent for how a twenty-questions multi-slot fix should conjoin.
- The one real fragility, in credit and in any future multi-slot: the guard is **manual** — "a
  corroboration/contestable node feeding the verdict via `⊗ₖ`, this guard set MUST extend." It is safe
  today because the guards cover today's contestable nodes, but it is a hand-maintained discipline, not a
  structural guarantee. A per-concept-slot gate would be the more robust form than replicating the
  extend-the-guard-set pattern.

This retires the correlation caveat on the finding: `⊗ₖ` laundering a glut is not merely Claude reasoning
agreeing with itself — it is written in the shipped code.

## How this applies to twenty questions

The finding lands differently on the current game than on the proposed fix, and the difference is the
point.

- **Today's game does not use `⊗ₖ` at all.** It is single-slot: one `Testimony[Answer]`, `corner` reading
  slot totals, gate = `corner = True ∧ cardinality = 1 ∧ ≥ 2 backers`. There is no derivation, so there is
  no laundering in the shipped code. Faced with the apple/tomato conflict — plant-evidence and fruit-
  evidence forced into one slot — a refute lands con on the slot total, the corner reads `Glut`, and the
  gate **blocks (inconclusive).** That is the honest, fail-closed outcome; there is no CWS today. The only
  fault is that "honest inconclusive after a wasted game" is a poor experience.
- **The proposal built naively with `⊗ₖ` would make twenty questions worse.** Decompose into
  `organism-identity` and `fruit-identity` and conjoin with `⊗ₖ`, and a gluted organism-slot conjoined
  with a resolved fruit-slot launders to a clean `True` → the gate would sign "apple" while the agents are
  still fighting over whether the target is the tree. A case the current game correctly blocks becomes a
  confident wrong signature — and, because the composite reads clean, the convergence monitor never fires
  to trigger the re-decomposition the proposal relies on.
- **The corrected fix restores the right behavior.** Sign "apple" iff each concept-slot independently gates
  — `organism-identity = apple-tree` resolved and `fruit-identity = apple-fruit` resolved. Both clean →
  sign; organism-slot gluted → that slot's gate blocks → hold. This never signs around a live contradiction
  about what the thing is, and it mirrors the credit pipeline's proven pattern.
- **Practically, multi-slot is premature for the toy.** It is a large build, safe only in the corrected
  form, and justified mainly by parsimony (finding 7 — the bilattice buys no measured metric gap over a
  fair control). The apple/tomato failure is better addressed now by the move already designed: wire the
  convergence monitor's flag to *act*. A compound referent conflated into one slot produces exactly the
  persistent-glut thrash the monitor detects (it fired correctly at e-59), so on that flag, hand back to
  the human — "trouble converging; reconsider an early answer — is the target the plant or the fruit?" That
  resolves the compound-referent jam with no rebuild and no laundering risk, under the same detect-not-
  decide discipline every other fix here follows.

## Risks 1–5

1. **Interaction with the shipped fixes — genuine blocker.** Contains the laundering finding, plus a
   second prong: `distinctBackers`, retirement, and the no-lone-sign floor are all defined over one
   `Testimony[Answer]` and have no multi-slot semantics; they would need re-deriving per `(slot,
   candidate)`. The floor "protects" the composite today only by accident (no event asserts the composite,
   so it has zero backers and abstains).
2. **Over-decomposition / explosion — build-ordering, coupled to #1.** Needs a hard depth/count cap and
   localize-to-the-thrashing-slot. The direction is conditional: with `∧ₜ`, more slots is more fail-closed;
   with `⊗ₖ`, more slots is a wider fail-open. Do not call it "fails safe" until #1 is fixed.
3. **Conjunction structure as semantic judgment — design constraint.** Slot choice and the node function
   are semantic; quarantine them in the agent's proposal, fixed at proposal time, and let the gate read
   only mechanical structure (reuse `ConceptScope`'s harness-authored discipline). Not a blocker.
4. **Metamorphic invariance — non-issue, conditional on #3.** The mechanical layer is content-blind; it
   passes provided the node function and slot assignment are treated as fixed structure, not recomputed
   from content.
5. **Flag hand-off unwired — build-ordering.** Confirmed: `ConvergenceWarning` projects to `Nil`,
   belief-inert, and nothing consumes it. A prerequisite, not a hazard; keep it belief-inert and wire
   monitor → agent → new assert events, never into belief.

## Decision — sequenced

**Wire the convergence flag's hand-off first; multi-slot decomposition is not the immediate build.**

1. Make the convergence monitor *act* — surface the non-convergence (detected correctly at e-59) to the
   human for premise re-examination. Zero multi-slot rebuild, already designed, and the right scope for the
   apple/tomato failure now.
2. If multi-slot is later pursued, decompose and **gate per concept-slot** (or conjoin the contested case
   with `∧ₜ`), and **delete the `⊗ₖ`+Theorem-6.7 safety claim entirely.** The single-slot fixes
   (retirement, the floor, the monitor) must be re-derived per `(slot, candidate)` first.
3. Independently of the toy, note the credit `⊗ₖ` guard is a manual, must-extend discipline; a per-node
   gate would be the more robust form if that path grows new contestable nodes.

## Correlation caveat and the running-system check

The evaluation is Claude reasoning over one corpus, so lens concurrence is not independent confirmation —
but the finding is source-anchored (Def 6.5, Theorem 6.7's proof, Remark 6.8, `derive`/`truthMeet`) and,
for the glut-laundering point specifically, confirmed in the shipped credit code, so the caveat is retired
on the decisive claim. Before any corrected multi-slot design is called validated, the running-system test
is the worked glut case — organism-slot `Both`, fruit-slot `True` — driven through the actual fold→gate
under both `derive` and `truthMeet`, confirming the signatures diverge (derive signs, truthMeet blocks) as
predicted.

*Recorded 2026-07-07 after the committee evaluation and the credit-path empirical check.*
